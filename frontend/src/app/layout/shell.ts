import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { AuthService } from '../core/auth.service';

interface NavItem {
  label: string;
  hint: string;
  path: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonModule, TagModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.user;
  readonly navOpen = signal(false);

  readonly initials = computed(() => {
    const name = this.user()?.fullName ?? '';
    return name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('');
  });

  readonly roleLabel = computed(() => {
    switch (this.user()?.role) {
      case 'CLAIMANT':
        return 'Claimant';
      case 'OFFICER':
        return 'Claims officer';
      case 'MANAGER':
        return 'Manager';
      default:
        return '';
    }
  });

  readonly workspaceLabel = computed(() => {
    switch (this.user()?.role) {
      case 'CLAIMANT':
        return 'Claimant portal';
      case 'OFFICER':
        return 'Claims operations';
      case 'MANAGER':
        return 'Portfolio intelligence';
      default:
        return 'Claims platform';
    }
  });

  readonly navItems = computed<NavItem[]>(() => {
    switch (this.user()?.role) {
      case 'CLAIMANT':
        return [
          {
            label: 'My claims',
            hint: 'Track and respond',
            path: '/claimant',
            icon: 'pi pi-file-edit',
          },
        ];
      case 'OFFICER':
        return [
          {
            label: 'Claims workspace',
            hint: 'Queue and decisions',
            path: '/officer',
            icon: 'pi pi-briefcase',
          },
        ];
      case 'MANAGER':
        return [
          {
            label: 'Portfolio overview',
            hint: 'Exposure and allocation',
            path: '/manager',
            icon: 'pi pi-chart-bar',
          },
        ];
      default:
        return [];
    }
  });

  toggleNav(): void {
    this.navOpen.update((open) => !open);
  }

  closeNav(): void {
    this.navOpen.set(false);
  }

  logout(): void {
    this.auth.logout();
  }
}
