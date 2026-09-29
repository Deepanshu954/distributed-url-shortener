import { z } from 'zod';

const restrictedAliases = [
  'api',
  'auth',
  'actuator',
  'swagger-ui',
  'swagger',
  'metrics',
  'health',
  'admin',
  'v3',
];

export const normalizeUrl = (url: string) => {
  let trimmed = (url || '').trim();
  if (!trimmed) return trimmed;
  if (!/^https?:\/\//i.test(trimmed)) {
    trimmed = `https://${trimmed}`;
  }
  return trimmed;
};

export const linkSchema = z.object({
  longUrl: z
    .string()
    .min(1, 'Please enter a URL to shorten')
    .max(8192, 'URL too long')
    .transform(normalizeUrl)
    .refine((url) => {
      try {
        const u = new URL(url);
        return u.protocol === 'http:' || u.protocol === 'https:';
      } catch {
        return false;
      }
    }, 'Invalid website address. Example: google.com or https://example.com'),
  customAlias: z
    .string()
    .regex(/^[0-9a-zA-Z_-]{4,30}$/, 'Alias must be 4-30 characters (alphanumeric, dash, underscore)')
    .refine((alias) => !restrictedAliases.includes(alias.toLowerCase()), 'This alias is reserved')
    .optional()
    .or(z.literal('')),
  ttlType: z.enum(['none', 'ttl', 'expiresAt']),
  ttlSeconds: z.number().positive().optional().or(z.literal('')),
  expiresAt: z.string().optional().or(z.literal('')),
});

export type LinkFormData = z.infer<typeof linkSchema>;
