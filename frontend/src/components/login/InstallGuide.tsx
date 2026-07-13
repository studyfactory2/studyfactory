import { useEffect, useMemo, useState } from 'react';

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
};

export function InstallGuide() {
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(false);
  const [message, setMessage] = useState('');
  const isStandalone = useMemo(() => {
    const navigatorWithStandalone = window.navigator as Navigator & { standalone?: boolean };

    return window.matchMedia('(display-mode: standalone)').matches
      || navigatorWithStandalone.standalone === true;
  }, []);
  const isIos = useMemo(() => /iphone|ipad|ipod/i.test(window.navigator.userAgent), []);

  useEffect(() => {
    const handleBeforeInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as BeforeInstallPromptEvent);
      setMessage('');
    };
    const handleInstalled = () => {
      setInstalled(true);
      setInstallPrompt(null);
      setMessage('홈 화면에 앱이 추가되었습니다.');
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleInstalled);
    };
  }, []);

  const install = async () => {
    if (installed || isStandalone) {
      setMessage('이미 앱으로 실행 중입니다.');
      return;
    }

    if (!installPrompt) {
      if (isIos) {
        setMessage('iOS에서는 Safari에서 공유 버튼을 누른 뒤 홈 화면에 추가를 선택해주세요.');
        return;
      }

      setMessage('브라우저 메뉴에서 공유 또는 설치를 눌러 홈 화면에 추가해주세요.');
      return;
    }

    await installPrompt.prompt();
    const choice = await installPrompt.userChoice;
    setInstallPrompt(null);

    if (choice.outcome === 'accepted') {
      setInstalled(true);
      setMessage('홈 화면에 앱이 추가되었습니다.');
      return;
    }

    setMessage('설치를 취소했습니다.');
  };

  return (
    <div className="install-guide">
      <button className="install-button" type="button" onClick={install}>
        <span className="button-icon home-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" focusable="false">
            <path d="M4 11.5 12 5l8 6.5" />
            <path d="M6.5 10.5V20h11v-9.5" />
            <path d="M10 20v-5h4v5" />
          </svg>
        </span>
        홈 화면에 추가하기
      </button>
      {message && <p>{message}</p>}
    </div>
  );
}
