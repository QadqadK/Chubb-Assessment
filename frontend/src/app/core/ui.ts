import { HttpErrorResponse } from '@angular/common/http';
import { ClaimStatus, ClaimType } from './models';

export type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

export function statusSeverity(status: ClaimStatus | string | null | undefined): TagSeverity {
  switch (status) {
    case 'APPROVED':
    case 'SETTLED':
      return 'success';
    case 'UNDER_REVIEW':
      return 'info';
    case 'INFO_REQUESTED':
    case 'ASSESSED':
      return 'warn';
    case 'REJECTED':
      return 'danger';
    default:
      return 'secondary';
  }
}

export function statusColor(status: ClaimStatus): string {
  const colors: Record<ClaimStatus, string> = {
    SUBMITTED: '#64748b',
    UNDER_REVIEW: '#1f5fa8',
    INFO_REQUESTED: '#d97706',
    ASSESSED: '#7c3aed',
    APPROVED: '#0e9f6e',
    REJECTED: '#dc2626',
    SETTLED: '#0f766e',
  };
  return colors[status] ?? '#64748b';
}

export function statusLabel(status: string | null | undefined): string {
  if (!status) {
    return '—';
  }
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function claimTypeLabel(type: ClaimType | string | null | undefined): string {
  if (type === 'MOTOR') {
    return 'Motor';
  }
  if (type === 'PROPERTY') {
    return 'Property';
  }
  return type ?? '—';
}

export function money(value: number | null | undefined, currency: string | null | undefined): string {
  if (value === null || value === undefined || !currency) {
    return '—';
  }
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${currency} ${value.toLocaleString('en-US', { maximumFractionDigits: 2 })}`;
  }
}

export function compactMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  if (Math.abs(value) >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}m`;
  }
  if (Math.abs(value) >= 1_000) {
    return `${(value / 1_000).toFixed(1)}k`;
  }
  return value.toFixed(0);
}

export function shortDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(value));
}

export function dateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

export function ageLabel(days: number | null | undefined): string {
  const value = days ?? 0;
  if (value <= 0) {
    return 'today';
  }
  return value === 1 ? '1 day' : `${value} days`;
}

export function bytesLabel(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { message?: string } | null;
    if (body?.message) {
      return body.message;
    }
    if (error.status === 0) {
      return 'Cannot reach the API. Check that the backend services are running.';
    }
    return error.statusText || fallback;
  }
  return fallback;
}

export function currencySummary(amounts: Record<string, number> | null | undefined): string {
  if (!amounts) {
    return '—';
  }
  const entries = Object.entries(amounts);
  if (entries.length === 0) {
    return '0';
  }
  return entries.map(([currency, amount]) => money(amount, currency)).join(' · ');
}

export function eventIcon(eventType: string): string {
  const icons: Record<string, string> = {
    CLAIM_SUBMITTED: 'pi pi-send',
    CLAIM_ASSIGNED: 'pi pi-user-plus',
    INFO_REQUESTED: 'pi pi-question-circle',
    INFO_PROVIDED: 'pi pi-comment',
    CLAIM_ASSESSED: 'pi pi-clipboard',
    CLAIM_DECIDED: 'pi pi-verified',
    CLAIM_SETTLED: 'pi pi-check-circle',
    DOCUMENT_UPLOADED: 'pi pi-paperclip',
  };
  return icons[eventType] ?? 'pi pi-circle';
}
