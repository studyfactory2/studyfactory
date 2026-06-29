#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${CLOUDFRONT_DISTRIBUTION_ID:-}" ]]; then
  echo "CLOUDFRONT_DISTRIBUTION_ID is required."
  exit 1
fi

if [[ -z "${EC2_HOST:-}" ]]; then
  echo "EC2_HOST is required."
  exit 1
fi

distribution_id="${CLOUDFRONT_DISTRIBUTION_ID}"
backend_host="${EC2_HOST#http://}"
backend_host="${backend_host#https://}"
backend_host="${backend_host%%/*}"
backend_host="${backend_host%%:*}"
aws_region="${AWS_REGION:-ap-northeast-2}"

if [[ "${backend_host}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  backend_host="ec2-${backend_host//./-}.${aws_region}.compute.amazonaws.com"
fi

origin_id="studyfactory-backend-api"

cache_policy_id="$(aws cloudfront list-cache-policies \
  --type managed \
  --query "CachePolicyList.Items[?CachePolicy.CachePolicyConfig.Name=='Managed-CachingDisabled'].CachePolicy.Id | [0]" \
  --output text)"

origin_request_policy_id="$(aws cloudfront list-origin-request-policies \
  --type managed \
  --query "OriginRequestPolicyList.Items[?OriginRequestPolicy.OriginRequestPolicyConfig.Name=='Managed-AllViewerExceptHostHeader'].OriginRequestPolicy.Id | [0]" \
  --output text)"

if [[ -z "${cache_policy_id}" || "${cache_policy_id}" == "None" ]]; then
  echo "Managed-CachingDisabled cache policy was not found."
  exit 1
fi

if [[ -z "${origin_request_policy_id}" || "${origin_request_policy_id}" == "None" ]]; then
  echo "Managed-AllViewerExceptHostHeader origin request policy was not found."
  exit 1
fi

workdir="$(mktemp -d)"
trap 'rm -rf "${workdir}"' EXIT

aws cloudfront get-distribution-config \
  --id "${distribution_id}" \
  > "${workdir}/distribution.json"

etag="$(jq -r '.ETag' "${workdir}/distribution.json")"

jq \
  --arg originId "${origin_id}" \
  --arg backendHost "${backend_host}" \
  --arg cachePolicyId "${cache_policy_id}" \
  --arg originRequestPolicyId "${origin_request_policy_id}" \
  '
  .DistributionConfig
  | .Origins.Items =
      (
        (.Origins.Items // [])
        | map(select(.Id != $originId))
        + [{
            Id: $originId,
            DomainName: $backendHost,
            OriginPath: "",
            CustomHeaders: {Quantity: 0},
            CustomOriginConfig: {
              HTTPPort: 8080,
              HTTPSPort: 443,
              OriginProtocolPolicy: "http-only",
              OriginSslProtocols: {
                Quantity: 1,
                Items: ["TLSv1.2"]
              },
              OriginReadTimeout: 30,
              OriginKeepaliveTimeout: 5
            },
            ConnectionAttempts: 3,
            ConnectionTimeout: 10,
            OriginShield: {
              Enabled: false
            }
          }]
      )
  | .Origins.Quantity = (.Origins.Items | length)
  | .CacheBehaviors.Items =
      (
        (.CacheBehaviors.Items // [])
        | map(select(.PathPattern != "/api/*"))
        | [{
            PathPattern: "/api/*",
            TargetOriginId: $originId,
            TrustedSigners: {
              Enabled: false,
              Quantity: 0
            },
            TrustedKeyGroups: {
              Enabled: false,
              Quantity: 0
            },
            ViewerProtocolPolicy: "redirect-to-https",
            AllowedMethods: {
              Quantity: 7,
              Items: ["GET", "HEAD", "OPTIONS", "PUT", "PATCH", "POST", "DELETE"],
              CachedMethods: {
                Quantity: 2,
                Items: ["GET", "HEAD"]
              }
            },
            SmoothStreaming: false,
            Compress: true,
            LambdaFunctionAssociations: {
              Quantity: 0
            },
            FunctionAssociations: {
              Quantity: 0
            },
            FieldLevelEncryptionId: "",
            CachePolicyId: $cachePolicyId,
            OriginRequestPolicyId: $originRequestPolicyId
          }] + .
      )
  | .CacheBehaviors.Quantity = (.CacheBehaviors.Items | length)
  ' "${workdir}/distribution.json" > "${workdir}/config.json"

aws cloudfront update-distribution \
  --id "${distribution_id}" \
  --if-match "${etag}" \
  --distribution-config "file://${workdir}/config.json"
