import { useState, useEffect, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Link } from 'react-router-dom';
import {
  Link as LinkIcon,
  ArrowRight,
  Copy,
  Check,
  ExternalLink,
  QrCode,
  BarChart3,
  Sparkles,
  Server,
  Zap,
  ShieldCheck,
  Download,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { normalizeUrl } from '@/lib/validators';
import { getLocalLinks, saveLocalLink, type LinkItem } from '@/hooks/use-links';
import { QRCodeSVG } from 'qrcode.react';
import { getDirectShortUrl, formatShortUrlDisplay } from '@/lib/short-url';

const formSchema = z.object({
  longUrl: z
    .string()
    .min(1, 'Please enter a web address to shorten')
    .max(8192, 'URL is too long')
    .refine((val) => {
      try {
        const u = new URL(normalizeUrl(val));
        return u.protocol === 'http:' || u.protocol === 'https:';
      } catch {
        return false;
      }
    }, 'Invalid web address (e.g. google.com or https://example.com)'),
  customAlias: z
    .string()
    .regex(/^[0-9a-zA-Z_-]{4,30}$/, 'Alias must be 4-30 characters (alphanumeric, dash, underscore)')
    .optional()
    .or(z.literal('')),
  ttl: z.string().optional(),
});

type FormValues = {
  longUrl: string;
  customAlias?: string;
  ttl?: string;
};

export default function Landing() {
  const [recentLinks, setRecentLinks] = useState<LinkItem[]>([]);
  const [createdLink, setCreatedLink] = useState<LinkItem | null>(null);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showQrModal, setShowQrModal] = useState(false);
  const qrRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    setRecentLinks(getLocalLinks());
  }, []);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      longUrl: '',
      customAlias: '',
      ttl: 'none',
    },
  });

  const handleCopy = (code: string) => {
    const shortUrl = getDirectShortUrl(code, createdLink?.shortUrl);
    navigator.clipboard.writeText(shortUrl).then(
      () => {
        setCopiedCode(code);
        toast.success('Direct short link copied to clipboard!');
        setTimeout(() => setCopiedCode(null), 2500);
      },
      () => toast.error('Failed to copy to clipboard')
    );
  };

  const downloadQrCode = (code: string) => {
    const svg = qrRef.current;
    if (!svg) return;
    const svgData = new XMLSerializer().serializeToString(svg);
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    const img = new Image();
    img.onload = () => {
      canvas.width = 400;
      canvas.height = 400;
      if (ctx) {
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, 400, 400);
        const pngFile = canvas.toDataURL('image/png');
        const downloadLink = document.createElement('a');
        downloadLink.download = `qr-${code}.png`;
        downloadLink.href = pngFile;
        downloadLink.click();
        toast.success('QR Code downloaded!');
      }
    };
    img.src = 'data:image/svg+xml;base64,' + btoa(svgData);
  };

  const onSubmit = async (data: FormValues) => {
    try {
      const payload: Record<string, unknown> = {
        longUrl: normalizeUrl(data.longUrl),
      };

      if (data.customAlias && data.customAlias.trim()) {
        payload.customAlias = data.customAlias.trim();
      }

      if (data.ttl === '1h') payload.ttlSeconds = 3600;
      else if (data.ttl === '24h') payload.ttlSeconds = 86400;
      else if (data.ttl === '7d') payload.ttlSeconds = 604800;
      else if (data.ttl === '30d') payload.ttlSeconds = 2592000;

      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : String(Date.now());

      const res = await api.post<LinkItem>('/api/links', payload, {
        headers: { 'Idempotency-Key': idempotencyKey },
      });

      const newLink = res.data;
      setCreatedLink(newLink);
      saveLocalLink(newLink);
      setRecentLinks(getLocalLinks());
      toast.success('Short link generated successfully!');
      reset();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { error?: string }; status?: number } };
      const msg = error.response?.data?.error || 'Failed to shorten URL. Please check input.';
      toast.error(msg);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-[calc(100vh-4rem)] text-center px-4 py-12 sm:py-20 relative overflow-hidden">
      {/* Background ambient glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[350px] bg-gradient-to-tr from-indigo-500/10 via-purple-500/15 to-pink-500/10 blur-[120px] pointer-events-none rounded-full" />

      <div className="max-w-4xl space-y-10 relative z-10 w-full">
        {/* Badges */}
        <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-border/80 bg-muted/50 text-xs font-medium backdrop-blur shadow-sm">
          <Sparkles className="h-3.5 w-3.5 text-indigo-500" />
          <span>Distributed Architecture</span>
          <span className="text-muted-foreground">•</span>
          <span className="text-emerald-500 font-semibold">Zero Dependencies Required</span>
        </div>

        {/* Hero Title */}
        <div className="space-y-4">
          <h1 className="text-4xl sm:text-6xl md:text-7xl font-extrabold tracking-tight leading-[1.1]">
            Scale Your Links. <br />
            <span className="bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 bg-clip-text text-transparent">
              Lightning Fast & Frictionless.
            </span>
          </h1>
          <p className="text-lg sm:text-xl text-muted-foreground max-w-2xl mx-auto">
            High-throughput URL shortener powered by Snowflake 64-bit sequencers, Murmur3 consistent hashing, and anti-stampede caching.
          </p>
        </div>

        {/* Shorten Form Card */}
        <Card className="w-full max-w-2xl mx-auto shadow-2xl border-indigo-500/20 bg-card/80 backdrop-blur">
          <CardContent className="p-4 sm:p-6 space-y-4">
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
              <div className="flex flex-col sm:flex-row gap-2">
                <Input
                  type="text"
                  placeholder="Paste your long link here (e.g. https://github.com)..."
                  className="h-14 text-base sm:text-lg bg-background/80 border-border/80 focus-visible:ring-2 focus-visible:ring-indigo-500 px-4"
                  {...register('longUrl')}
                />
                <Button
                  type="submit"
                  size="lg"
                  className="h-14 px-8 text-base font-semibold shrink-0 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white shadow-lg shadow-indigo-500/25"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Shortening...' : 'Shorten'}
                  <ArrowRight className="ml-2 h-5 w-5" />
                </Button>
              </div>

              {errors.longUrl && (
                <p className="text-sm text-destructive text-left px-1 font-medium">{errors.longUrl.message}</p>
              )}

              {/* Advanced Options Toggle */}
              <div className="text-left">
                <button
                  type="button"
                  onClick={() => setShowAdvanced(!showAdvanced)}
                  className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground font-medium transition-colors"
                >
                  {showAdvanced ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                  {showAdvanced ? 'Hide advanced settings' : 'Custom alias & expiration options'}
                </button>
              </div>

              {showAdvanced && (
                <div className="pt-2 border-t border-border/50 grid grid-cols-1 sm:grid-cols-2 gap-3 text-left">
                  <div>
                    <label className="text-xs font-semibold text-muted-foreground mb-1 block">
                      Custom Alias (Optional)
                    </label>
                    <Input
                      type="text"
                      placeholder="e.g. my-project-v2"
                      className="h-10 text-sm bg-background/50"
                      {...register('customAlias')}
                    />
                    {errors.customAlias && (
                      <p className="text-xs text-destructive mt-1">{errors.customAlias.message}</p>
                    )}
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-muted-foreground mb-1 block">
                      Link Expiration
                    </label>
                    <select
                      className="w-full h-10 rounded-md border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                      {...register('ttl')}
                    >
                      <option value="none">Never expires</option>
                      <option value="1h">1 Hour</option>
                      <option value="24h">24 Hours</option>
                      <option value="7d">7 Days</option>
                      <option value="30d">30 Days</option>
                    </select>
                  </div>
                </div>
              )}
            </form>
          </CardContent>
        </Card>

        {/* Instant Result Card */}
        {createdLink && (
          <Card className="w-full max-w-2xl mx-auto border-emerald-500/30 bg-emerald-500/5 shadow-xl text-left overflow-hidden animate-in fade-in slide-in-from-top-4 duration-300">
            <CardHeader className="pb-3 border-b border-emerald-500/20">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-emerald-500 font-semibold text-sm">
                  <Check className="h-4 w-4" /> Link Shortened Successfully!
                </div>
                <span className="text-xs font-mono text-muted-foreground">Snowflake 64-bit ID</span>
              </div>
            </CardHeader>
            <CardContent className="p-4 sm:p-6 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3 rounded-lg bg-background border border-border/80">
                <div className="flex items-center gap-3 overflow-hidden">
                  <div className="h-10 w-10 rounded-lg bg-indigo-500/10 text-indigo-500 flex items-center justify-center shrink-0">
                    <LinkIcon className="h-5 w-5" />
                  </div>
                  <div className="overflow-hidden">
                    <a
                      href={getDirectShortUrl(createdLink.shortCode, createdLink.shortUrl)}
                      target="_blank"
                      rel="noreferrer"
                      className="font-bold text-lg text-primary hover:underline flex items-center gap-1.5 truncate"
                    >
                      <span className="truncate">{formatShortUrlDisplay(createdLink.shortCode, createdLink.shortUrl)}</span>
                      <ExternalLink className="h-4 w-4 shrink-0 opacity-70" />
                    </a>
                    <p className="text-xs text-muted-foreground truncate" title={createdLink.longUrl}>
                      {createdLink.longUrl}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <Button
                    variant={copiedCode === createdLink.shortCode ? 'default' : 'secondary'}
                    size="sm"
                    onClick={() => handleCopy(createdLink.shortCode)}
                    className="gap-1.5"
                  >
                    {copiedCode === createdLink.shortCode ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                    {copiedCode === createdLink.shortCode ? 'Copied' : 'Copy'}
                  </Button>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setShowQrModal(!showQrModal)}
                    className="gap-1.5"
                  >
                    <QrCode className="h-4 w-4" /> QR
                  </Button>

                  <Button variant="ghost" size="sm" asChild className="gap-1.5">
                    <Link to={`/links/${createdLink.shortCode}`}>
                      <BarChart3 className="h-4 w-4" /> Stats
                    </Link>
                  </Button>
                </div>
              </div>

              {/* Collapsible QR Code Display */}
              {showQrModal && (
                <div className="p-4 rounded-xl bg-background border border-border/80 flex flex-col items-center gap-4 text-center">
                  <div className="p-3 bg-white rounded-lg shadow-md">
                    <QRCodeSVG
                      ref={qrRef}
                      value={getDirectShortUrl(createdLink.shortCode, createdLink.shortUrl)}
                      size={180}
                      level="H"
                      includeMargin
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => downloadQrCode(createdLink.shortCode)}
                      className="gap-1.5"
                    >
                      <Download className="h-4 w-4" /> Download PNG
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {/* Recent Links Section */}
        {recentLinks.length > 0 && (
          <div className="pt-4 w-full max-w-2xl mx-auto text-left space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold tracking-tight">Your Recent Links</h3>
              <Link to="/dashboard" className="text-xs text-primary hover:underline font-medium">
                View All in Dashboard →
              </Link>
            </div>
            <div className="space-y-2">
              {recentLinks.slice(0, 3).map((link) => {
                const isCopied = copiedCode === link.shortCode;
                return (
                  <div
                    key={link.shortCode}
                    className="flex items-center justify-between p-3.5 rounded-lg border border-border/80 bg-card hover:border-border transition-colors text-card-foreground shadow-sm"
                  >
                    <div className="flex flex-col overflow-hidden mr-3">
                      <a
                        href={getDirectShortUrl(link.shortCode, link.shortUrl)}
                        target="_blank"
                        rel="noreferrer"
                        className="font-semibold text-primary text-sm hover:underline flex items-center gap-1.5 truncate"
                      >
                        <span className="truncate">{formatShortUrlDisplay(link.shortCode, link.shortUrl)}</span>
                        <ExternalLink className="h-3 w-3 shrink-0 opacity-70" />
                      </a>
                      <span className="text-xs text-muted-foreground truncate mt-0.5" title={link.longUrl}>
                        {link.longUrl}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <Button
                        variant={isCopied ? 'default' : 'outline'}
                        size="sm"
                        onClick={() => handleCopy(link.shortCode)}
                        className="h-8 px-2.5 text-xs gap-1"
                      >
                        {isCopied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
                        {isCopied ? 'Copied' : 'Copy'}
                      </Button>
                      <Button variant="ghost" size="sm" asChild className="h-8 px-2.5 text-xs">
                        <Link to={`/links/${link.shortCode}`}>
                          <BarChart3 className="h-3.5 w-3.5" />
                        </Link>
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Architecture Spec Grid (CTO Portfolio Value-Add) */}
        <div className="pt-10 border-t border-border/50 text-left">
          <div className="mb-6 text-center">
            <h2 className="text-xl font-bold tracking-tight">Enterprise Distributed Architecture</h2>
            <p className="text-sm text-muted-foreground">Built to scale to 10,000+ QPS with strict latency SLOs</p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card className="bg-card/50 border-border/60">
              <CardHeader className="pb-2">
                <Server className="h-5 w-5 text-indigo-500 mb-1" />
                <CardTitle className="text-sm font-semibold">Snowflake 64-bit ID</CardTitle>
                <CardDescription className="text-xs">
                  41-bit epoch delta + 10-bit Node ID + 12-bit sequence. 4,096,000 unique IDs/sec/node without central DB locking.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card className="bg-card/50 border-border/60">
              <CardHeader className="pb-2">
                <Zap className="h-5 w-5 text-purple-500 mb-1" />
                <CardTitle className="text-sm font-semibold">Consistent Hash Ring</CardTitle>
                <CardDescription className="text-xs">
                  Murmur3_32 hashing with 150 virtual nodes per database shard. Deterministic routing & seamless shard autoscaling.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card className="bg-card/50 border-border/60">
              <CardHeader className="pb-2">
                <ShieldCheck className="h-5 w-5 text-pink-500 mb-1" />
                <CardTitle className="text-sm font-semibold">Cache-Aside & Stampede Lock</CardTitle>
                <CardDescription className="text-xs">
                  Distributed SETNX anti-stampede locks, 60s negative caching against penetration, and Resilience4j circuit breakers.
                </CardDescription>
              </CardHeader>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
