# Demo Setup & Login Credentials

## ✅ Demo Accounts Ready for Testing

### Super Admin
- **Email:** `superadmin@healthcarehub.test`
- **Password:** `Demo@12345`
- **Role:** Super Admin (full system access)
- **Permissions:** All features, all hospitals, system configuration

### Hospital Admin
- **Email:** `admin@apollohospitals.test`
- **Password:** `Demo@12345`
- **Role:** Hospital Admin
- **Hospital:** Apollo Hospitals Delhi
- **Permissions:** Hospital management, staff management, finances

### Doctor
- **Email:** `doctor.rajesh@apollohospitals.test`
- **Password:** `Demo@12345`
- **Role:** Doctor
- **Specialization:** Cardiology
- **Hospital:** Apollo Hospitals Delhi
- **Permissions:** Patient consultations, prescriptions, reports

### Receptionist  
- **Email:** `receptionist.priya@apollohospitals.test`
- **Password:** `Demo@12345`
- **Role:** Receptionist
- **Hospital:** Apollo Hospitals Delhi
- **Permissions:** Appointment booking, patient registration

### Patient
- **Email:** `patient.amit@gmail.test`
- **Password:** `Demo@12345`
- **Role:** Patient
- **Hospital:** Apollo Hospitals Delhi
- **Permissions:** Personal medical records, appointment booking, consultations

---

## How to Create Demo Accounts

### Option 1: Via Supabase Auth

1. Go to your Supabase dashboard
2. Navigate to Authentication → Users
3. Click "Add user"
4. Create each account with the credentials above
5. Set their email as verified (for testing)
6. Update `raw_user_meta_data` to include:
   ```json
   {
     "first_name": "First Name",
     "last_name": "Last Name",
     "role": "patient" or "doctor" or "hospital_admin" etc
   }
   ```

### Option 2: Via Application Signup

1. Visit `/auth/signup`
2. Register with demo email and password
3. Select appropriate role during signup
4. Complete profile information
5. Test account is ready!

---

## Demo Data Populated

✅ **3 Hospitals**
- Apollo Hospitals Delhi
- Fortis Healthcare Mumbai  
- Max Healthcare Bangalore
- Medanta Gurugram
- CARE Hospitals Hyderabad

✅ **15+ Departments** per hospital
- Cardiology, Neurology, Orthopedics, Oncology
- Pediatrics, Gynecology, General Surgery
- Emergency & Trauma, ICU, Pathology, Radiology
- And more...

✅ **30+ Doctors** across departments
- With specializations, qualifications, ratings
- Available schedules and consultation fees

✅ **90+ Patients** with full profiles
- Medical history, allergies, chronic conditions
- Family members, emergency contacts
- Insurance policies

✅ **300+ Appointments** scheduled
- Various statuses (scheduled, completed, cancelled)
- Across different consultationtypes

✅ **300+ Consultations** with details
- Connected to appointments
- Diagnosis, symptoms, treatment plans
- Follow-up dates

✅ **500+ Prescriptions** issued
- With medicines and dosages
- Connected to consultations
- Expiry tracking

✅ **500+ Medical Reports**
- Blood reports, CT scans, X-rays
- ECG, Ultrasound, Discharge summaries
- With findings and recommendations

✅ **200+ Insurance Policies**
- Different providers
- Active policies with coverage amounts
- Claims tracking

✅ **300+ Notifications**
- Appointment reminders
- Medicine alerts
- Test results notifications
- Emergency alerts

✅ **100+ Medicines**
- Indian pharmaceutical brands
- With dosage forms and strengths
- Side effects and contraindications

---

## What You Can Test

### As Super Admin
- View all hospitals and their performance
- Access analytics and dashboards
- Manage system settings
- View all users and roles
- Generate system reports

### As Hospital Admin
- Manage doctors and staff
- View patient management dashboard
- Access billing and revenue reports
- Create announcements
- Manage appointments
- View hospital statistics

### As Doctor
- View assigned patients
- Book consultations
- Issue prescriptions
- Upload medical reports
- Write consultation notes
- Add AI summaries and voice notes

### As Receptionist
- Book appointments for patients
- Register new patients
- Manage waiting list
- Send reminders
- Handle inquiries

### As Patient
- View health dashboard
- Search and book doctors
- View past appointments
- Access medical records
- View prescriptions
- Track consultations
- Read reports
- Manage profile

---

## Testing Workflows

### Appointment Booking Flow
1. Login as Patient
2. Go to Dashboard → Find Doctor
3. Search for "Rajesh" (Doctor account)
4. Book an appointment
5. Receive confirmation notification

### Doctor Consultation Flow
1. Login as Doctor
2. View today's appointments
3. Start consultation
4. Write notes and diagnosis
5. Issue prescription
6. Upload medical report

### Admin Dashboard
1. Login as Hospital Admin
2. View hospital statistics
3. See patient metrics
4. Check department status
5. View revenue and finances

---

## Notes

- All demo accounts use password: `Demo@12345`
- Email verification is pre-configured for demo accounts
- Data is fully interconnected (no orphan records)
- You can modify/delete demo data freely
- New data can be added via the application

---

## Troubleshooting

**Can't login?**
- Verify email matches exactly (case-sensitive in some systems)
- Check password is entered correctly
- Ensure Supabase Auth is configured
- Check .env variables are set

**Missing data in dashboard?**
- Refresh the page (Ctrl+F5)
- Clear browser cache
- Check RLS policies in Supabase
- Verify user role in database

**Need more test data?**
- Duplicate existing records
- Generate more via SQL scripts
- Add records through the UI

---

**Ready to test? Go to:** `http://localhost:3000/auth/login`

Login with any demo account above and explore the futuristic Hospital Admin Portal!
