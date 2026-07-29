# HealthCare Hub - Deployment Guide

This guide covers deploying the HealthCare Hub application to Vercel with Supabase backend.

## Prerequisites

- Vercel account (https://vercel.com)
- Supabase project (https://supabase.com)
- GitHub account (optional but recommended)
- Domain name (optional)

## Step 1: Prepare Supabase Project

### 1.1 Create Supabase Project

1. Visit https://supabase.com and sign in
2. Click "New Project"
3. Fill in project details:
   - **Name**: HealthCare Hub Production
   - **Database Password**: Generate strong password (save securely)
   - **Region**: Choose closest to your users (e.g., Asia-Singapore)
4. Wait for project to be created (2-3 minutes)

### 1.2 Get Connection Details

1. Go to Project Settings → API
2. Copy these values:
   - **Project URL**: `https://xxxxx.supabase.co`
   - **Anon Public Key**: `eyJhbGci...`
   - **Service Role Key**: `eyJhbGci...` (keep secret!)

### 1.3 Run Database Migrations

1. Navigate to SQL Editor in Supabase dashboard
2. Run the provided migration files in order:
   - `001_create_schema.sql` - Creates all tables
   - `002_create_rls_policies_and_indexes.sql` - Adds security and performance
   - `003_seed_hospital_data.sql` - Adds sample data

3. Verify migrations:
   - Check Tables section shows all tables
   - Check SQL shows no errors

### 1.4 Configure Email (Optional but Recommended)

1. Go to Authentication → Email Templates
2. Customize:
   - Confirm signup email
   - Reset password email
   - Change email address email

3. For production, configure SMTP:
   - Settings → Email
   - Enable "Enable external Postgres database"
   - Or use Supabase SMTP

## Step 2: Deploy to Vercel

### 2.1 Connect GitHub (Recommended)

1. Push code to GitHub:
```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/yourusername/healthcare-hub
git push -u origin main
```

### 2.2 Deploy via Vercel Dashboard

1. Visit https://vercel.com/new
2. Import GitHub repository
3. Configure project:
   - **Project Name**: healthcare-hub
   - **Framework**: Next.js
   - **Root Directory**: ./
4. Set environment variables (see Step 3)
5. Click Deploy

**OR via Vercel CLI:**

```bash
npm i -g vercel
vercel login
vercel --prod
```

### 2.3 Custom Domain (Optional)

1. In Vercel dashboard → Domains
2. Click "Add Custom Domain"
3. Enter your domain (e.g., healthcare-hub.com)
4. Follow DNS instructions for your provider
5. Wait for DNS propagation (5-48 hours)

## Step 3: Environment Variables

### For Vercel Dashboard

1. Go to Project Settings → Environment Variables
2. Add the following variables:

```env
# Public (visible in browser)
NEXT_PUBLIC_SUPABASE_URL=https://xxxxx.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJhbGci...
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=eyJhbGci...
NEXT_PUBLIC_DEV_SUPABASE_REDIRECT_URL=https://yourdomain.com/auth/callback

# Secret (server-only)
SUPABASE_SERVICE_ROLE_KEY=eyJhbGci...
SUPABASE_JWT_SECRET=your-jwt-secret
```

3. Select "Production" for each variable
4. Redeploy after adding variables

### For Local Development

Create `.env.local`:

```env
# Supabase (Dev)
NEXT_PUBLIC_SUPABASE_URL=https://dev-xxxxx.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=dev-anon-key
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=dev-pub-key
NEXT_PUBLIC_DEV_SUPABASE_REDIRECT_URL=http://localhost:3000/auth/callback

# Server-side
SUPABASE_SERVICE_ROLE_KEY=dev-service-role-key
SUPABASE_JWT_SECRET=dev-jwt-secret
```

## Step 4: Security Configuration

### 4.1 Enable RLS (Row Level Security)

All RLS policies are already created in migration 002. Verify:

1. Supabase → Authentication → Policies
2. Confirm policies exist for:
   - profiles
   - doctors
   - patients
   - appointments
   - consultations
   - prescriptions
   - reports
   - vitals
   - notifications
   - doctor_chat
   - medicines
   - hospitals
   - departments

### 4.2 Authentication Settings

1. Go to Authentication → Providers
2. Ensure "Email" is enabled
3. Configure Redirect URLs:
   - Production: `https://yourdomain.com/auth/callback`
   - Development: `http://localhost:3000/auth/callback`

4. Optional: Configure OAuth providers
   - Google OAuth (recommended)
   - GitHub OAuth
   - Azure AD

### 4.3 CORS Configuration

Supabase handles CORS automatically for:
- `https://yourdomain.com`
- `http://localhost:3000` (dev only)

No additional configuration needed.

### 4.4 SSL/TLS Certificate

- Vercel automatically provides SSL certificate
- Enabled by default
- Redirects all HTTP to HTTPS
- Certificate auto-renews

## Step 5: Database Backups

### Automatic Backups

Supabase includes:
- **Daily backups**: 7 day retention
- **Weekly backups**: 4 week retention
- **Monthly backups**: 1 year retention

### Manual Backups

1. Supabase Dashboard → Database → Backups
2. Click "Backup now"
3. Download backup (pg_dump format)

### Restore from Backup

1. In Backups section, click "Restore"
2. Select backup version
3. Confirm (will overwrite current database)
4. Wait for restore to complete

## Step 6: Monitoring & Logging

### Vercel Analytics

1. Enable in Vercel dashboard
2. Automatic collection of:
   - Web Vitals
   - Page performance
   - Edge function performance

### Supabase Monitoring

1. Dashboard → Database → Monitoring
2. View:
   - Query performance
   - Database size
   - Connection count
   - Resource usage

### Error Tracking

1. Set up error monitoring service:
   - Sentry (recommended)
   - LogRocket
   - Datadog

2. Configure in Next.js:
```typescript
// next.config.mjs
import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: process.env.SENTRY_DSN,
  environment: process.env.NODE_ENV,
});
```

## Step 7: Performance Optimization

### Database Indexes

All indexes created in migration 002:
- Email lookups
- Role filtering
- Hospital associations
- Appointment queries
- Consultation queries

### Caching Strategy

- Implement SWR for client-side caching
- Use Next.js ISR (Incremental Static Regeneration)
- Configure CDN caching for static assets

### Image Optimization

- Use Next.js Image component
- Automatic AVIF conversion
- Responsive image serving
- Edge caching enabled

## Step 8: Database Scalability

### Connection Pooling

For production, use Supabase connection pooler:
1. Settings → Database → Connection pooling
2. Enable "Connection pooling"
3. Mode: "Transaction" (for most apps)

### Read Replicas

For high-traffic scenarios:
1. Create read replicas from Supabase UI
2. Update connection strings for read-heavy queries
3. Use primary for writes only

### Vertical Scaling

When limits approached:
1. Upgrade Supabase plan
2. Increase compute resources
3. Increase storage allocation

## Step 9: CI/CD Pipeline

### Automatic Deployment

Every push to `main` triggers:
1. GitHub Actions runs tests
2. Vercel builds application
3. Vercel runs preview deployment
4. After approval, merges to production

### Manual Deployment

```bash
# Deploy to production
vercel --prod

# View deployment logs
vercel logs
```

### Rollback

```bash
# List deployments
vercel list

# Rollback to previous deployment
vercel rollback
```

## Step 10: Health Checks

### Endpoint Monitoring

Create a health check endpoint:

```typescript
// app/api/health/route.ts
import { createClient } from '@/lib/supabase/server'

export async function GET() {
  const supabase = await createClient()
  
  try {
    const { data } = await supabase
      .from('hospitals')
      .select('id')
      .limit(1)
    
    return Response.json({ status: 'healthy', db: 'connected' })
  } catch (error) {
    return Response.json(
      { status: 'unhealthy', error: 'Database connection failed' },
      { status: 503 }
    )
  }
}
```

### Uptime Monitoring

1. Configure Vercel Speed Insights
2. Add external monitoring:
   - UptimeRobot
   - StatusPage.io
   - PagerDuty

## Troubleshooting

### Deployment Fails

1. Check Vercel build logs
2. Verify environment variables are set
3. Run `pnpm build` locally to reproduce
4. Check for TypeScript errors: `pnpm tsc --noEmit`

### Database Connection Issues

1. Verify `NEXT_PUBLIC_SUPABASE_URL` is correct
2. Check `NEXT_PUBLIC_SUPABASE_ANON_KEY` is valid
3. Verify Supabase project is active
4. Check firewall/IP restrictions

### Authentication Issues

1. Verify redirect URL matches exactly
2. Check if email is verified in Supabase
3. Review RLS policies for permission errors
4. Check browser cookies for session token

### Performance Issues

1. Check Vercel Analytics for bottlenecks
2. Review Supabase query performance
3. Check database indexes exist
4. Monitor connection pooling status
5. Consider adding caching layer

## Production Checklist

- [ ] Supabase project created and secured
- [ ] All migrations applied successfully
- [ ] RLS policies verified
- [ ] Email authentication configured
- [ ] Environment variables set in Vercel
- [ ] Custom domain configured (if applicable)
- [ ] SSL certificate installed
- [ ] Backups configured
- [ ] Monitoring and alerts set up
- [ ] Database indexes verified
- [ ] CDN caching enabled
- [ ] Error tracking configured
- [ ] Health check endpoint active
- [ ] Uptime monitoring enabled
- [ ] Disaster recovery plan documented

## Support Resources

- Vercel Docs: https://vercel.com/docs
- Supabase Docs: https://supabase.com/docs
- Next.js Docs: https://nextjs.org/docs
- GitHub Issues: Link to repo
- Community Discord: (if applicable)

---

Last Updated: 2026-07-23
Version: 1.0.0
