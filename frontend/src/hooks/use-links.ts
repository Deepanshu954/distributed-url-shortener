import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

export interface LinkItem {
  shortCode: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
  expiresAt: string | null;
  customAlias?: boolean;
}

export interface LinksPage {
  content: LinkItem[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// Local history helper for instant guest persistence
export const getLocalLinks = (): LinkItem[] => {
  try {
    return JSON.parse(localStorage.getItem('my_shortened_links') || '[]');
  } catch {
    return [];
  }
};

export const saveLocalLink = (link: LinkItem) => {
  try {
    const list = getLocalLinks();
    const updated = [link, ...list.filter((l) => l.shortCode !== link.shortCode)].slice(0, 100);
    localStorage.setItem('my_shortened_links', JSON.stringify(updated));
  } catch (err) {
    console.error('Failed to save to localStorage', err);
  }
};

export const removeLocalLink = (code: string) => {
  try {
    const list = getLocalLinks();
    localStorage.setItem('my_shortened_links', JSON.stringify(list.filter((l) => l.shortCode !== code)));
  } catch (err) {
    console.error('Failed to remove from localStorage', err);
  }
};

export const useLinks = (page: number = 0, size: number = 20) => {
  return useQuery({
    queryKey: ['links', page, size],
    queryFn: async () => {
      const { data } = await api.get<LinksPage>(`/api/links?page=${page}&size=${size}`);
      return data;
    },
  });
};

export const useLink = (code: string) => {
  return useQuery({
    queryKey: ['link', code],
    queryFn: async () => {
      const { data } = await api.get<LinkItem>(`/api/links/${code}`);
      return data;
    },
    enabled: !!code,
  });
};

export const useCreateLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: {
      longUrl: string;
      customAlias?: string;
      ttlSeconds?: number;
      expiresAt?: string;
      idempotencyKey?: string;
    }) => {
      const { idempotencyKey, ...rest } = payload;
      const headers: Record<string, string> = {};
      if (idempotencyKey) {
        headers['Idempotency-Key'] = idempotencyKey;
      }
      const { data } = await api.post<LinkItem>('/api/links', rest, { headers });
      saveLocalLink(data);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['links'] });
    },
  });
};

export const useUpdateLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      code,
      ...payload
    }: {
      code: string;
      longUrl?: string;
      ttlSeconds?: number;
      expiresAt?: string;
    }) => {
      const { data } = await api.put<LinkItem>(`/api/links/${code}`, payload);
      saveLocalLink(data);
      return data;
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['links'] });
      queryClient.invalidateQueries({ queryKey: ['link', variables.code] });
    },
  });
};

export const useDeleteLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (code: string) => {
      await api.delete(`/api/links/${code}`);
      removeLocalLink(code);
    },
    onSuccess: (_, code) => {
      queryClient.invalidateQueries({ queryKey: ['links'] });
      queryClient.invalidateQueries({ queryKey: ['link', code] });
    },
  });
};
