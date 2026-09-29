import axios, { AxiosError } from 'axios';
import { toast } from 'sonner';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ error?: string }>) => {
    // Handle Rate Limit (429)
    if (error.response?.status === 429) {
      const retryAfter = error.response.headers['retry-after'] || 'a few';
      toast.error(`Rate limit exceeded. Please try again in ${retryAfter} seconds.`);
      return Promise.reject(error);
    }

    // Handle Service Unavailable (503)
    if (error.response?.status === 503) {
      toast.error('System temporarily overloaded or undergoing maintenance. Please retry.');
      return Promise.reject(error);
    }

    return Promise.reject(error);
  }
);
