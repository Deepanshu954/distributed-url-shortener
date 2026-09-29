import { useParams, useNavigate, Link } from 'react-router-dom';
import { useLink, useDeleteLink, useUpdateLink } from '@/hooks/use-links';
import { useLinkStats } from '@/hooks/use-stats';
import { StatsChart } from '@/components/stats-chart';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { ArrowLeft, ExternalLink, Copy, Check, Trash2, QrCode, Download } from 'lucide-react';
import { toast } from 'sonner';
import { useState, useEffect, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';

export default function LinkDetail() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();

  const { data: link, isLoading, isError } = useLink(code || '');
  const { data: stats } = useLinkStats(code || '');
  const deleteMutation = useDeleteLink();
  const updateMutation = useUpdateLink();

  const [longUrl, setLongUrl] = useState('');
  const [copied, setCopied] = useState(false);
  const qrRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (link) {
      setLongUrl(link.longUrl);
    }
  }, [link]);

  useEffect(() => {
    if (isError) {
      toast.error('Link not found');
      navigate('/dashboard');
    }
  }, [isError, navigate]);

  if (isError) {
    return null;
  }

  if (isLoading || !link) {
    return (
      <div className="container max-w-5xl mx-auto px-4 py-8 space-y-6">
        <Skeleton className="h-10 w-1/3" />
        <div className="grid md:grid-cols-2 gap-6">
          <Skeleton className="h-64 rounded-xl" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      </div>
    );
  }

  const getFullShortUrl = (shortCode: string) => {
    const origin = window.location.origin;
    const pathname = window.location.pathname.endsWith('/')
      ? window.location.pathname
      : `${window.location.pathname}/`;
    return `${origin}${pathname}#/${shortCode}`;
  };

  const shortUrl = getFullShortUrl(link.shortCode);

  const handleCopy = () => {
    navigator.clipboard.writeText(shortUrl).then(
      () => {
        setCopied(true);
        toast.success('Copied to clipboard');
        setTimeout(() => setCopied(false), 2000);
      },
      () => toast.error('Failed to copy')
    );
  };

  const downloadQrCode = () => {
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
        downloadLink.download = `qr-${link.shortCode}.png`;
        downloadLink.href = pngFile;
        downloadLink.click();
        toast.success('QR Code downloaded!');
      }
    };
    img.src = 'data:image/svg+xml;base64,' + btoa(svgData);
  };

  const handleDelete = () => {
    if (confirm(`Are you sure you want to delete /${link.shortCode}?`)) {
      deleteMutation.mutate(link.shortCode, {
        onSuccess: () => {
          toast.success('Link deleted');
          navigate('/dashboard');
        },
        onError: () => toast.error('Failed to delete link'),
      });
    }
  };

  const handleUpdate = () => {
    updateMutation.mutate(
      { code: link.shortCode, longUrl },
      {
        onSuccess: () => toast.success('Destination URL updated'),
        onError: (err: unknown) => {
          const error = err as { response?: { data?: { error?: string } } };
          toast.error(error.response?.data?.error || 'Failed to update link');
        },
      }
    );
  };

  return (
    <div className="container max-w-5xl mx-auto px-4 sm:px-8 py-8 space-y-8">
      {/* Header bar */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-border/50 pb-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" asChild className="h-9 w-9">
            <Link to="/dashboard">
              <ArrowLeft className="h-5 w-5" />
            </Link>
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-bold font-mono tracking-tight">{window.location.host}/#/{link.shortCode}</h1>
              {link.customAlias && (
                <span className="text-xs font-semibold px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-500 border border-indigo-500/20">
                  Custom Alias
                </span>
              )}
            </div>
            <p className="text-xs text-muted-foreground mt-0.5">
              Created {new Intl.DateTimeFormat('default', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(link.createdAt))}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={handleCopy} className="gap-1.5">
            {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
            {copied ? 'Copied' : 'Copy'}
          </Button>

          <Button variant="secondary" size="sm" asChild className="gap-1.5">
            <a href={shortUrl} target="_blank" rel="noreferrer">
              <ExternalLink className="h-4 w-4" /> Open Link
            </a>
          </Button>

          <Button
            variant="destructive"
            size="sm"
            onClick={handleDelete}
            disabled={deleteMutation.isPending}
            className="gap-1.5"
          >
            <Trash2 className="h-4 w-4" /> Delete
          </Button>
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        {/* Left Column: Link Settings + QR Code */}
        <div className="space-y-6">
          <Card className="bg-card/80 border-border/80">
            <CardHeader>
              <CardTitle className="text-base font-bold">Link Configuration</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">Short Code</Label>
                <Input value={link.shortCode} disabled className="font-mono bg-muted/30" />
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">Destination Long URL</Label>
                <div className="flex gap-2">
                  <Input
                    value={longUrl}
                    onChange={(e) => setLongUrl(e.target.value)}
                    className="font-mono text-sm"
                  />
                  <Button variant="outline" size="icon" asChild className="shrink-0">
                    <a href={link.longUrl} target="_blank" rel="noreferrer">
                      <ExternalLink className="h-4 w-4" />
                    </a>
                  </Button>
                </div>
              </div>

              {link.expiresAt && (
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Expiration Date</Label>
                  <Input
                    value={new Intl.DateTimeFormat('default', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(link.expiresAt))}
                    disabled
                    className="bg-muted/30"
                  />
                </div>
              )}

              <Button
                onClick={handleUpdate}
                disabled={longUrl === link.longUrl || updateMutation.isPending}
                className="w-full mt-2 bg-gradient-to-r from-indigo-600 to-purple-600 text-white"
              >
                {updateMutation.isPending ? 'Saving...' : 'Update Destination URL'}
              </Button>
            </CardContent>
          </Card>

          {/* QR Code Card */}
          <Card className="bg-card/80 border-border/80">
            <CardHeader className="pb-2">
              <CardTitle className="text-base font-bold flex items-center gap-2">
                <QrCode className="h-4 w-4 text-indigo-500" />
                QR Code
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col sm:flex-row items-center gap-4">
              <div className="p-3 bg-white rounded-lg shadow-sm border shrink-0">
                <QRCodeSVG
                  ref={qrRef}
                  value={shortUrl}
                  size={120}
                  level="H"
                  includeMargin
                />
              </div>
              <div className="space-y-2 text-center sm:text-left">
                <p className="text-xs text-muted-foreground">
                  Scan to immediately navigate to the shortened destination on mobile devices.
                </p>
                <Button variant="outline" size="sm" onClick={downloadQrCode} className="gap-1.5">
                  <Download className="h-3.5 w-3.5" /> Download PNG
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Real-time Analytics */}
        <div>
          {stats ? (
            <StatsChart stats={stats} />
          ) : (
            <Card className="h-64 flex items-center justify-center">
              <Skeleton className="h-48 w-full m-4" />
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
