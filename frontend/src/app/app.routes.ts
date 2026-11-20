import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./layout/login.page').then((m) => m.LoginPage),
    canActivate: [guestGuard],
    title: 'Sign in · Chubb Claims',
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      {
        path: 'claimant',
        loadComponent: () =>
          import('./claimant/claimant.page').then((m) => m.ClaimantPage),
        canActivate: [roleGuard(['CLAIMANT'])],
        title: 'My claims · Chubb Claims',
      },
      {
        path: 'officer',
        loadComponent: () =>
          import('./officer/officer.page').then((m) => m.OfficerPage),
        canActivate: [roleGuard(['OFFICER'])],
        title: 'Claims workspace · Chubb Claims',
      },
      {
        path: 'manager',
        loadComponent: () =>
          import('./manager/manager.page').then((m) => m.ManagerPage),
        canActivate: [roleGuard(['MANAGER'])],
        title: 'Portfolio overview · Chubb Claims',
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
