import { createClient } from '@/lib/supabase/server'
import { NextResponse } from 'next/server'

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url)
  const code = searchParams.get('code')
  const origin = request.headers.get('origin') || ''

  if (code) {
    const supabase = await createClient()
    const { error } = await supabase.auth.exchangeCodeForSession(code)
    if (!error) {
      const forwardedHost = request.headers.get('x-forwarded-host')
      const proto = request.headers.get('x-forwarded-proto')
      const host = forwardedHost || new URL(request.url).host
      const protocol = proto || 'https'

      return NextResponse.redirect(`${protocol}://${host}/dashboard`)
    }
  }

  return NextResponse.redirect(`${origin}/auth/error`)
}
