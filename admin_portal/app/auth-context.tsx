'use client'

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { createClient } from '@/lib/supabase/client'
import type { Session, User } from '@supabase/supabase-js'

interface UserProfile {
  id: string
  full_name: string
  role: 'super_admin' | 'hospital_admin' | 'staff'
}

interface AuthContextType {
  session: Session | null
  user: User | null
  profile: UserProfile | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<void>
  signUp: (email: string, password: string, firstName: string, lastName: string) => Promise<void>
  signOut: () => Promise<void>
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [user, setUser] = useState<User | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  
  // Only create supabase client if env vars are available
  let supabase: ReturnType<typeof createClient> | null = null
  try {
    if (process.env.NEXT_PUBLIC_SUPABASE_URL && process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY) {
      supabase = createClient()
    }
  } catch (error) {
    console.error('[v0] Failed to create Supabase client:', error)
  }

  // Initialize auth state on mount
  useEffect(() => {
    const initializeAuth = async () => {
      if (!supabase) {
        setLoading(false)
        return
      }

      try {
        // Get current session
        const { data: { session: currentSession }, error: sessionError } = await supabase.auth.getSession()
        
        if (sessionError) {
          console.error('[v0] Session error:', sessionError)
          setLoading(false)
          return
        }

        if (currentSession) {
          setSession(currentSession)
          setUser(currentSession.user)
          
          // Fetch user profile
          const { data: profileData, error: profileError } = await supabase
            .from('profiles')
            .select('id, full_name, role')
            .eq('id', currentSession.user.id)
            .maybeSingle()

          if (!profileError && profileData) {
            setProfile(profileData)
          }
        }
      } catch (error) {
        console.error('[v0] Auth initialization error:', error)
      } finally {
        setLoading(false)
      }
    }

    initializeAuth()

    if (!supabase) return

    // Subscribe to auth changes
    const { data: { subscription } } = supabase.auth.onAuthStateChange(async (event, newSession) => {
      console.log('[v0] Auth state changed:', event)
      setSession(newSession)
      setUser(newSession?.user ?? null)

      if (newSession?.user && supabase) {
        // Fetch profile when session changes
        const { data: profileData } = await supabase
          .from('profiles')
          .select('id, full_name, role')
          .eq('id', newSession.user.id)
          .maybeSingle()

        setProfile(profileData ?? null)
      } else {
        setProfile(null)
      }
    })

    return () => {
      subscription?.unsubscribe()
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    if (!supabase) throw new Error('Supabase not initialized')
    
    const { error } = await supabase.auth.signInWithPassword({
      email,
      password,
    })

    if (error) {
      throw error
    }
  }, [supabase])

  const signUp = useCallback(async (email: string, password: string, firstName: string, lastName: string) => {
    if (!supabase) throw new Error('Supabase not initialized')
    
    const { error: signUpError } = await supabase.auth.signUp({
      email,
      password,
      options: {
        emailRedirectTo: `${window.location.origin}/auth/callback`,
        data: {
          first_name: firstName,
          last_name: lastName,
          role: 'staff', // Default role for new signups
        },
      },
    })

    if (signUpError) {
      throw signUpError
    }

    // The database trigger creates a profile securely for every new auth user.
  }, [supabase])

  const signOut = useCallback(async () => {
    if (!supabase) throw new Error('Supabase not initialized')
    
    const { error } = await supabase.auth.signOut()
    if (error) {
      throw error
    }
    setSession(null)
    setUser(null)
    setProfile(null)
  }, [supabase])

  return (
    <AuthContext.Provider
      value={{
        session,
        user,
        profile,
        loading,
        signIn,
        signUp,
        signOut,
        isAuthenticated: !!user && !!session,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
