import type { ChangeEvent, FormEvent } from 'react';
import { useEffect, useState } from 'react';
import { apiRequest } from '../api/client';
import type { Branch, LoginResponse, PreRegistrationVerifyResponse } from '../types/domain';
import { decodeTokenPayload } from '../utils/token';

export type LoginMode = 'login' | 'verify' | 'password';
export type Message = { text: string; type: 'info' | 'success' | 'error' };

export type LoginFormState = {
  loginName: string;
  loginPassword: string;
  signupName: string;
  signupBranchId: string;
  signupPassword: string;
  signupPasswordConfirm: string;
};

const INITIAL_FORM: LoginFormState = {
  loginName: '',
  loginPassword: '',
  signupName: '',
  signupBranchId: '',
  signupPassword: '',
  signupPasswordConfirm: '',
};

export function useLoginScreen() {
  const [mode, setMode] = useState<LoginMode>('login');
  const [form, setForm] = useState<LoginFormState>(INITIAL_FORM);
  const [verifiedMember, setVerifiedMember] = useState<PreRegistrationVerifyResponse | null>(null);
  const [verifiedMembers, setVerifiedMembers] = useState<PreRegistrationVerifyResponse[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branchLoading, setBranchLoading] = useState(false);
  const [branchLoaded, setBranchLoaded] = useState(false);
  const [branchDropdownOpen, setBranchDropdownOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<Message | null>(null);

  useEffect(() => {
    if (mode !== 'verify' || branches.length > 0 || branchLoading || branchLoaded) {
      return;
    }

    void loadBranches();
  }, [mode, branches.length, branchLoading, branchLoaded]);

  const changeForm = (key: keyof LoginFormState) => (event: ChangeEvent<HTMLInputElement>) => {
    if (key === 'signupName') {
      setVerifiedMember(null);
      setVerifiedMembers([]);
    }

    setForm((current) => ({ ...current, [key]: event.target.value }));
  };

  const selectBranch = (branchId: number) => {
    setVerifiedMember(null);
    setVerifiedMembers([]);
    setForm((current) => ({ ...current, signupBranchId: String(branchId) }));
    setBranchDropdownOpen(false);
  };

  const showMessage = (text: string, type: Message['type'] = 'info') => {
    setMessage({ text, type });
  };

  const openLogin = () => {
    setMode('login');
    setBranchDropdownOpen(false);
    setVerifiedMember(null);
    setVerifiedMembers([]);
    setMessage(null);
  };

  const openVerify = () => {
    setMode('verify');
    setBranchDropdownOpen(false);
    setVerifiedMember(null);
    setMessage(null);
  };

  const selectVerifiedMember = (member: PreRegistrationVerifyResponse) => {
    setVerifiedMember(member);
    setMode('password');
    showMessage('사전등록 정보를 확인했습니다. 사용할 비밀번호를 설정해주세요.', 'success');
  };

  const loadBranches = async () => {
    setBranchLoading(true);
    try {
      const branchResponses = await apiRequest<Branch[]>('/api/branches');
      setBranches(branchResponses);
      if (branchResponses.length > 0) {
        setForm((current) => {
          if (current.signupBranchId) {
            return current;
          }

          return { ...current, signupBranchId: String(branchResponses[0].id) };
        });
      }
    } catch {
      showMessage('지점 목록을 불러오지 못했습니다.', 'error');
    } finally {
      setBranchLoading(false);
      setBranchLoaded(true);
    }
  };

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form.loginName.trim() || !form.loginPassword.trim()) {
      showMessage('이름과 비밀번호를 입력해주세요.', 'error');
      return;
    }

    setLoading(true);
    setMessage(null);
    setVerifiedMember(null);
    setVerifiedMembers([]);
    try {
      const tokens = await apiRequest<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          name: form.loginName.trim(),
          password: form.loginPassword,
        }),
      });
      const payload = decodeTokenPayload(tokens.accessToken);
      localStorage.setItem('accessToken', tokens.accessToken);
      localStorage.setItem('refreshToken', tokens.refreshToken);
      localStorage.setItem('memberName', payload.name || form.loginName.trim());
      localStorage.setItem('memberId', payload.sub ? String(payload.sub) : '');
      localStorage.setItem('branchId', payload.branchId ? String(payload.branchId) : '');
      localStorage.setItem('memberRole', payload.role || '');

      if (payload.role === 'ADMIN') {
        window.location.href = '/managerdashboard?view=attendance';
        return;
      }
      if (payload.role === 'STAFF') {
        window.location.href = '/managerdashboard?view=attendance';
        return;
      }

      window.location.href = '/memberdashboard';
    } catch (error) {
      setLoading(false);
      showMessage(error instanceof Error ? error.message : '로그인에 실패했습니다.', 'error');
    }
  };

  const handleVerifySignup = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form.signupName.trim() || !form.signupBranchId) {
      showMessage('이름과 지점을 입력해주세요.', 'error');
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      const members = await apiRequest<PreRegistrationVerifyResponse[]>('/api/members/pre-registration/verify', {
        method: 'POST',
        body: JSON.stringify({
          name: form.signupName.trim(),
          branchId: Number(form.signupBranchId),
        }),
      });
      const membersWithDisplayName = withDisplayNames(members);
      setVerifiedMembers(membersWithDisplayName);

      if (membersWithDisplayName.length === 1) {
        selectVerifiedMember(membersWithDisplayName[0]);
        return;
      }

      showMessage('같은 이름의 사전등록 정보가 있습니다. 본인 정보를 선택해주세요.', 'info');
    } catch (error) {
      showMessage(error instanceof Error ? error.message : '사전등록 정보를 찾지 못했습니다.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form.signupPassword || form.signupPassword.length !== 4) {
      showMessage('비밀번호는 4자리로 입력해주세요.', 'error');
      return;
    }
    if (form.signupPassword !== form.signupPasswordConfirm) {
      showMessage('비밀번호 확인이 일치하지 않습니다.', 'error');
      return;
    }
    if (!verifiedMember) {
      showMessage('사전등록 정보를 다시 확인해주세요.', 'error');
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      await apiRequest<void>('/api/members/signup', {
        method: 'POST',
        body: JSON.stringify({
          memberId: verifiedMember.memberId,
          password: form.signupPassword,
        }),
      });
      setForm((current) => ({
        ...current,
        loginName: verifiedMember.name,
        loginPassword: '',
        signupPassword: '',
        signupPasswordConfirm: '',
      }));
      setVerifiedMember(null);
      setVerifiedMembers([]);
      setMode('login');
      showMessage('가입이 완료되었습니다! 로그인해 주세요.', 'success');
    } catch (error) {
      showMessage(error instanceof Error ? error.message : '가입에 실패했습니다.', 'error');
    } finally {
      setLoading(false);
    }
  };

  return {
    branches,
    branchDropdownOpen,
    branchLoading,
    changeForm,
    form,
    handleLogin,
    handleSignup,
    handleVerifySignup,
    loading,
    message,
    mode,
    openLogin,
    openVerify,
    selectBranch,
    selectVerifiedMember,
    setBranchDropdownOpen,
    verifiedMember,
    verifiedMembers,
  };
}

function withDisplayNames(members: PreRegistrationVerifyResponse[]) {
  const nameCounts = new Map<string, number>();

  return members.map((member) => {
    const order = (nameCounts.get(member.name) || 0) + 1;
    nameCounts.set(member.name, order);

    return {
      ...member,
      displayName: order === 1 ? member.name : `${member.name}${order}`,
    };
  });
}
