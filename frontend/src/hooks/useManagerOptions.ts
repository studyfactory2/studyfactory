import { useEffect, useState } from 'react';
import { apiRequest } from '../api/client';
import type { Branch, NameplateContent } from '../types/domain';

export function useManagerOptions(enabled: boolean) {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [nameplates, setNameplates] = useState<NameplateContent[]>([]);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    void loadOptions();
  }, [enabled]);

  const loadOptions = async () => {
    try {
      const [branchResponses, nameplateResponses] = await Promise.all([
        apiRequest<Branch[]>('/api/branches'),
        apiRequest<NameplateContent[]>('/api/nameplate-contents'),
      ]);
      setBranches(branchResponses);
      setNameplates(nameplateResponses);
    } catch {
      setBranches([]);
      setNameplates([]);
    }
  };

  return { branches, nameplates };
}
