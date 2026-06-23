import { useEffect, useState } from 'react';
import { apiRequest } from '../api/client';
import type { Branch, Certification } from '../types/domain';

export function useManagerOptions(enabled: boolean) {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [certifications, setCertifications] = useState<Certification[]>([]);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    void loadOptions();
  }, [enabled]);

  const loadOptions = async () => {
    try {
      const [branchResponses, certificationResponses] = await Promise.all([
        apiRequest<Branch[]>('/api/branches'),
        apiRequest<Certification[]>('/api/certifications'),
      ]);
      setBranches(branchResponses);
      setCertifications(certificationResponses);
    } catch {
      setBranches([]);
      setCertifications([]);
    }
  };

  return { branches, certifications };
}
