import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '@/lib/api';

export default function RedirectHandler() {
  const { code } = useParams<{ code: string }>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!code) return;

    // Fetch link metadata and trigger click event tracking
    api
      .get<{ longUrl: string }>(`/api/links/${code}`)
      .then((res) => {
        if (res.data && res.data.longUrl) {
          // Record click event via backend redirect endpoint
          try {
            const baseUrl = api.defaults.baseURL || '';
            fetch(`${baseUrl}/${code}`, { method: 'GET', redirect: 'manual' }).catch(() => {});
          } catch {
            // Ignore tracking fetch error
          }
          window.location.replace(res.data.longUrl);
        } else {
          setError('Short URL not found');
        }
      })
      .catch(() => {
        // Fallback to local storage if API is unreachable
        try {
          const recentLinks = JSON.parse(localStorage.getItem('my_shortened_links') || '[]');
          const found = recentLinks.find((l: { shortCode: string; longUrl: string }) => l.shortCode === code);
          if (found && found.longUrl) {
            window.location.replace(found.longUrl);
            return;
          }
        } catch {
          // Ignore
        }
        setError('Short link not found or has expired');
      });
  }, [code]);

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4">
        <div className="p-4 rounded-full bg-destructive/10 text-destructive mb-4">
          <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
        <h2 className="text-2xl font-bold mb-2">Link Not Found</h2>
        <p className="text-muted-foreground mb-6 max-w-md">{error}</p>
        <a
          href="#/"
          className="px-6 py-2.5 bg-primary text-primary-foreground rounded-lg font-medium hover:opacity-90 transition-opacity"
        >
          Create a New Short Link
        </a>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4 space-y-3">
      <div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent"></div>
      <p className="text-base font-medium text-muted-foreground">Redirecting to destination...</p>
    </div>
  );
}
