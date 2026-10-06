import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { getDirectShortUrl } from '@/lib/short-url';

export default function RedirectHandler() {
  const { code } = useParams<{ code: string }>();

  useEffect(() => {
    if (!code) return;
    // Immediately forward to direct server redirect endpoint (HTTP 302 Found)
    const directUrl = getDirectShortUrl(code);
    window.location.replace(directUrl);
  }, [code]);

  return null;
}

