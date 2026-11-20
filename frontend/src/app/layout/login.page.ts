import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/ui';

interface DemoAccount {
  label: string;
  email: string;
  role: string;
  icon: string;
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, InputTextModule, PasswordModule, ButtonModule],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['claimant.my@example.com', [Validators.required, Validators.email]],
    password: ['Password123!', Validators.required],
  });

  readonly demoAccounts: DemoAccount[] = [
    {
      label: 'Claimant',
      email: 'claimant.my@example.com',
      role: 'Report and track',
      icon: 'pi pi-user',
    },
    {
      label: 'Claims officer',
      email: 'officer.my1@example.com',
      role: 'Queue and decide',
      icon: 'pi pi-briefcase',
    },
    {
      label: 'Manager',
      email: 'manager.my@example.com',
      role: 'Portfolio and allocation',
      icon: 'pi pi-chart-bar',
    },
  ];

  useDemo(account: DemoAccount): void {
    this.form.setValue({ email: account.email, password: 'Password123!' });
    this.error.set(null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (response) => {
        void this.router.navigateByUrl(this.auth.homePath(response.user));
      },
      error: (error) => {
        this.loading.set(false);
        this.error.set(errorMessage(error, 'Sign in failed. Check your email and password.'));
      },
    });
  }
}
