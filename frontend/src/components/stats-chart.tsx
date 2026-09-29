import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { MousePointerClick, Clock, Globe, Activity } from 'lucide-react';

interface StatsChartProps {
  stats: {
    shortCode: string;
    clickCount: number;
    lastClickAt: string | null;
    lastReferrer: string | null;
  };
}

export function StatsChart({ stats }: { stats: StatsChartProps['stats'] }) {
  const data = [
    { name: 'Recorded Clicks', clicks: stats.clickCount },
  ];

  return (
    <div className="space-y-4">
      {/* Metric Cards Grid */}
      <div className="grid grid-cols-2 gap-3">
        <Card className="p-4 bg-card/60 border-border/80">
          <div className="flex items-center gap-2 text-indigo-500 mb-1">
            <MousePointerClick className="h-4 w-4" />
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Total Clicks</span>
          </div>
          <p className="text-2xl sm:text-3xl font-extrabold text-foreground">{stats.clickCount}</p>
        </Card>

        <Card className="p-4 bg-card/60 border-border/80">
          <div className="flex items-center gap-2 text-purple-500 mb-1">
            <Globe className="h-4 w-4" />
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Last Referrer</span>
          </div>
          <p className="text-sm font-semibold truncate text-foreground mt-1" title={stats.lastReferrer || 'Direct / None'}>
            {stats.lastReferrer || 'Direct / None'}
          </p>
        </Card>
      </div>

      <Card className="bg-card/80 border-border/80">
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-bold flex items-center gap-2">
              <Activity className="h-4 w-4 text-emerald-500" />
              Click Performance
            </CardTitle>
            <span className="text-xs font-medium px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
              Live Stream
            </span>
          </div>
          <CardDescription className="text-xs flex items-center gap-1.5 mt-1">
            <Clock className="h-3.5 w-3.5" />
            {stats.lastClickAt
              ? `Last accessed: ${new Intl.DateTimeFormat('default', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(stats.lastClickAt))}`
              : 'No traffic recorded yet'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="h-[180px] w-full pt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <XAxis dataKey="name" stroke="#888888" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis allowDecimals={false} stroke="#888888" fontSize={12} tickLine={false} axisLine={false} />
                <Tooltip
                  cursor={{ fill: 'rgba(99, 102, 241, 0.1)' }}
                  contentStyle={{ backgroundColor: 'hsl(var(--card))', borderRadius: '8px', border: '1px solid hsl(var(--border))' }}
                />
                <Bar dataKey="clicks" radius={[6, 6, 0, 0]} maxBarSize={80}>
                  <Cell fill="url(#clickGradient)" />
                </Bar>
                <defs>
                  <linearGradient id="clickGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#6366f1" stopOpacity={0.9} />
                    <stop offset="100%" stopColor="#a855f7" stopOpacity={0.6} />
                  </linearGradient>
                </defs>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
