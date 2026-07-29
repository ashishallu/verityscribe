# HealthCare Hub - Hospital Management Platform

A comprehensive healthcare platform for managing patient care, doctor consultations, appointments, medical records, and hospital operations across India.

## Features

### Patient Features
- **Doctor Search & Booking**: Browse certified doctors by specialization, location, and ratings
- **Appointment Management**: Schedule, reschedule, and cancel appointments
- **Medical Records**: Access complete health history with reports and test results
- **Digital Prescriptions**: View and manage prescriptions from doctors
- **Vital Tracking**: Monitor health metrics and vital signs
- **Online Consultations**: Connect with doctors via video, phone, or chat

### Doctor Features
- **Schedule Management**: Manage appointment calendar and availability
- **Patient Management**: View patient profiles and medical history
- **Consultations**: Conduct online and in-person consultations
- **Prescriptions**: Create and manage digital prescriptions
- **Medical Records**: Access patient reports and health history

### Hospital Admin Features
- **Hospital Management**: Configure hospital settings and departments
- **Doctor Management**: Onboard and manage doctors
- **Bed Management**: Track bed occupancy and allocation
- **Revenue Tracking**: Monitor financial metrics and payments
- **Department Oversight**: Manage departments and specializations

## Technology Stack

### Frontend
- **Framework**: Next.js 15.5.18 (App Router)
- **UI Library**: React 19
- **Styling**: Tailwind CSS v3
- **Components**: shadcn/ui
- **Forms**: React Hook Form + Zod validation
- **Icons**: Lucide React

### Backend
- **Database**: PostgreSQL via Supabase
- **Authentication**: Supabase Auth
- **ORM/Query**: Direct SQL with Supabase client
- **Hosting**: Vercel
- **Environment**: Node.js with pnpm

### Security Features
- **Row Level Security (RLS)**: Enforced at database level
- **Authentication**: Supabase Auth with email/password
- **Data Encryption**: HTTPS with TLS
- **User Roles**: 7-tier role-based access control
- **HIPAA Compliance**: Health data encryption and privacy

## Project Structure

```
/vercel/share/v0-project/
├── app/
│   ├── auth/              # Authentication pages (login, signup)
│   ├── dashboard/         # Main dashboard
│   ├── appointments/      # Appointment management
│   ├── doctors/          # Doctor browsing and management
│   ├── patients/         # Patient management
│   ├── consultations/    # Consultation management
│   ├── reports/          # Medical reports
│   ├── settings/         # User settings
│   ├── layout.tsx        # Root layout
│   ├── page.tsx          # Landing page
│   └── globals.css       # Global styles
├── components/
│   └── ui/              # shadcn/ui components
├── lib/
│   ├── supabase/        # Supabase client setup
│   │   ├── client.ts    # Browser client
│   │   ├── server.ts    # Server client
│   │   └── proxy.ts     # Middleware proxy
│   ├── types/           # TypeScript types
│   │   └── database.ts  # Database schema types
│   └── utils.ts         # Utility functions
├── middleware.ts         # Next.js middleware for auth
├── package.json         # Dependencies
└── README.md            # This file
```

## Getting Started

### Prerequisites
- Node.js 18+ with pnpm
- Supabase project (free tier available at https://supabase.com)
- Environment variables configured

### Installation

1. **Clone and Install**
```bash
git clone <repository-url>
cd vercel/share/v0-project
pnpm install
```

2. **Set Up Supabase**
   - Create a new Supabase project at https://supabase.com
   - Get your project URL and API keys from the Supabase dashboard
   - Copy the `.env.example` to `.env.local` (if exists) or create `.env.local`

3. **Configure Environment Variables**

Create `.env.local`:
```env
# Supabase
NEXT_PUBLIC_SUPABASE_URL=your_supabase_url
NEXT_PUBLIC_SUPABASE_ANON_KEY=your_supabase_anon_key
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=your_supabase_publishable_key
NEXT_PUBLIC_DEV_SUPABASE_REDIRECT_URL=http://localhost:3000/auth/callback

# Optional: Database connection string (for server-side operations)
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
```

4. **Run Database Migrations**
   
   The database schema, RLS policies, and seed data are already set up in your Supabase project:
   - Migration 001: Created all tables with proper relationships
   - Migration 002: Added RLS policies and performance indexes
   - Migration 003: Seeded realistic Indian hospital data

5. **Start Development Server**
```bash
pnpm dev
```

The application will be available at `http://localhost:3000`

## Database Schema

### Core Tables

#### Authentication & Profiles
- `profiles`: User profiles with roles (patient, doctor, admin, etc.)
- `roles`: Role definitions and permissions
- `permissions`: Permission mappings

#### Hospital Infrastructure
- `hospitals`: Hospital information and details
- `departments`: Hospital departments with bed management
- `file_categories`: Categories for medical documents

#### Medical Professionals
- `doctors`: Doctor profiles with qualifications
- `doctor_educations`: Doctor education history
- `doctor_specializations`: Doctor specializations

#### Patient Management
- `patients`: Patient profiles and demographics
- `allergies`: Patient allergies and reactions
- `chronic_conditions`: Ongoing health conditions
- `emergency_contacts`: Emergency contact information

#### Clinical Operations
- `appointments`: Appointment scheduling and tracking
- `consultations`: Consultation records and notes
- `vitals`: Patient vital signs monitoring
- `prescriptions`: Digital prescriptions
- `reports`: Medical test reports

#### Support Systems
- `medicines`: Medicine database with details
- `insurance_providers`: Insurance company information
- `insurance_policies`: Patient insurance policies
- `notifications`: User notifications
- `doctor_chat`: Doctor-patient messaging
- `system_settings`: Configuration settings

## User Roles & Access Control

### Role Hierarchy
1. **Super Admin**: Full system access
2. **Hospital Admin**: Hospital management
3. **Doctor**: Medical consultations
4. **Patient**: Patient care access
5. **Medical Staff**: Supporting roles (nurse, lab technician)
6. **Receptionist**: Appointment and admin support

### Row Level Security
- Patients see only their own records
- Doctors see patients they've consulted with
- Admins see hospital-scoped data
- All queries filtered by user role and hospital context

## API Endpoints (Planned)

### Authentication
- `POST /api/auth/login` - User login
- `POST /api/auth/signup` - User registration
- `POST /api/auth/logout` - User logout

### Appointments
- `GET /api/appointments` - List appointments
- `POST /api/appointments` - Create appointment
- `PUT /api/appointments/:id` - Update appointment
- `DELETE /api/appointments/:id` - Cancel appointment

### Doctors
- `GET /api/doctors` - List doctors
- `GET /api/doctors/:id` - Doctor details
- `POST /api/doctors` - Register doctor

### Patients
- `GET /api/patients` - List patients
- `GET /api/patients/:id` - Patient details
- `POST /api/patients` - Create patient record

### Medical Records
- `GET /api/reports` - Medical reports
- `GET /api/prescriptions` - Prescriptions
- `GET /api/vitals` - Vital signs

## Development Guidelines

### Code Style
- Use TypeScript for type safety
- Follow React best practices with hooks
- Use Server Components where possible
- Client components only for interactivity

### Component Structure
- Break down pages into smaller components
- Keep components in `components/` directory
- Use shadcn/ui for consistent styling
- Implement proper error boundaries

### Database Queries
- Use parameterized queries (Supabase client does this automatically)
- Implement proper error handling
- Use RLS for data security
- Cache data appropriately with SWR

### Testing
```bash
pnpm test          # Run tests
pnpm lint          # Run ESLint
```

## Deployment

### Deploy to Vercel
```bash
vercel deploy
```

### Environment Variables for Production
Set these in Vercel Project Settings:
- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`

### Database Backups
- Supabase provides automated daily backups
- Access backups from Supabase dashboard
- Regular monitoring of database health

## Common Tasks

### Add a New Page
1. Create folder: `app/new-feature/`
2. Create `page.tsx` with page component
3. Add navigation links in dashboard

### Query Supabase Data
```typescript
import { createClient } from '@/lib/supabase/client'

const supabase = createClient()
const { data, error } = await supabase
  .from('table_name')
  .select('*')
  .eq('column', 'value')
```

### Create a New Database Table
1. Access Supabase SQL editor
2. Write and execute migration SQL
3. Create TypeScript types in `lib/types/database.ts`
4. Add RLS policies for security

### Implement Authentication Check
```typescript
import { createClient } from '@/lib/supabase/server'

const supabase = await createClient()
const { data: { user } } = await supabase.auth.getUser()

if (!user) {
  // Redirect to login
}
```

## Troubleshooting

### Auth Issues
- Check Supabase project settings for correct redirects
- Verify `NEXT_PUBLIC_SUPABASE_URL` matches your project
- Clear browser cookies if stuck on login

### Database Connection
- Verify environment variables are set correctly
- Check Supabase dashboard for connection status
- Review RLS policies if getting permission errors

### Build Errors
- Run `pnpm install` to ensure all dependencies
- Check Next.js version compatibility
- Review TypeScript errors: `pnpm tsc --noEmit`

## Security Considerations

### Data Protection
- All patient data encrypted at rest and in transit
- HIPAA-compliant infrastructure
- Regular security audits
- Compliance with Indian data protection laws

### Authentication
- Passwords hashed with bcrypt
- Session tokens secured with HTTP-only cookies
- Multi-factor authentication ready
- OAuth integration possible

### Access Control
- Row-level security at database level
- Role-based access control enforced
- Audit logging of sensitive operations
- IP whitelisting available

## Contributing

1. Create a feature branch: `git checkout -b feature/name`
2. Make changes and test thoroughly
3. Submit pull request with description
4. Follow code style guidelines

## License

Proprietary - HealthCare Hub Platform

## Support

For issues and support:
1. Check existing documentation
2. Review GitHub issues
3. Contact development team
4. Visit support portal

## Roadmap

- [ ] Mobile app (React Native)
- [ ] Telemedicine integration (video consultation)
- [ ] Insurance claim processing automation
- [ ] AI-powered health recommendations
- [ ] Blockchain for medical records
- [ ] Advanced analytics dashboard
- [ ] Pharmacy integration
- [ ] Lab integration for direct ordering

## Changelog

### Version 1.0.0 (Current)
- Initial release with core features
- Database schema with RLS policies
- Authentication system
- Landing page and dashboard
- Placeholder pages for all modules

---

Built with ❤️ for better healthcare access across India
