import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { CLAIMS_API } from './api-config';
import { AuthSession, LoginResponse, Role } from './models';

const STORAGE_KEY = 'chubb.claims.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sessionState = signal<AuthSession | null>(this.restore());

  readonly session = this.sessionState.asReadonly();
  readonly user = computed(() => this.sessionState()?.user ?? null);
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);
  readonly isAuthenticated = computed(() => {
    const session = this.sessionState();
    return !!session && new Date(session.expiresAt).getTime() > Date.now();
  });
  readonly token = computed(() => (this.isAuthenticated() ? this.sessionState()?.token ?? null : null));

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${CLAIMS_API}/auth/login`, { email, password }).pipe(
      tap((response) => {
        const session: AuthSession = {
          token: response.token,
          expiresAt: response.expiresAt,
          user: response.user,
        };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.sessionState.set(session);
      }),
    );
  }

  logout(): void {
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  clearSession(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.sessionState.set(null);
  }

  homePath(user = this.user()): string {
    switch (user?.role) {
      case 'CLAIMANT':
        return '/claimant';
      case 'OFFICER':
        return '/officer';
      case 'MANAGER':
        return '/manager';
      default:
        return '/login';
    }
  }

  private restore(): AuthSession | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      const session = JSON.parse(raw) as AuthSession;
      if (!session.expiresAt || new Date(session.expiresAt).getTime() <= Date.now()) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }
}
