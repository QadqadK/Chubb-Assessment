import { formatDate } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  FormBuilder,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TimelineModule } from 'primeng/timeline';
import { forkJoin } from 'rxjs';
import { ClaimantApi } from '../core/claimant-api';
import {
  ClaimDetail,
  ClaimEventView,
  ClaimSummary,
  ClaimType,
  CreateClaimRequest,
  InfoRequestView,
  PolicyView,
} from '../core/models';
import {
  ageLabel,
  bytesLabel,
  claimTypeLabel,
  dateTime,
  errorMessage,
  eventIcon,
  money,
  shortDate,
  statusColor,
  statusLabel,
  statusSeverity,
} from '../core/ui';

@Component({
  selector: 'app-claimant',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    TableModule,
    TagModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TextareaModule,
    InputNumberModule,
    SelectModule,
    TimelineModule,
  ],
  templateUrl: './claimant.page.html',
  styleUrl: './claimant.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClaimantPage implements OnInit {
  private readonly api = inject(ClaimantApi);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(MessageService);

  readonly loading = signal(true);
  readonly claims = signal<ClaimSummary[]>([]);
  readonly policies = signal<PolicyView[]>([]);
  readonly detail = signal<ClaimDetail | null>(null);
  readonly detailVisible = signal(false);
  readonly detailLoading = signal(false);
  readonly submitVisible = signal(false);
  readonly submitting = signal(false);
  readonly selectedFile = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly respondingId = signal<string | null>(null);
  readonly drafts = signal<Record<string, string>>({});
  readonly incidentDate = signal(formatDate(new Date(), 'yyyy-MM-dd', 'en-US'));
  readonly todayIso = formatDate(new Date(), 'yyyy-MM-dd', 'en-US');

  readonly statusSeverity = statusSeverity;
  readonly statusLabel = statusLabel;
  readonly claimTypeLabel = claimTypeLabel;
  readonly money = money;
  readonly shortDate = shortDate;
  readonly dateTime = dateTime;
  readonly ageLabel = ageLabel;
  readonly bytesLabel = bytesLabel;
  readonly eventIcon = eventIcon;

  readonly submitForm = this.fb.group({
    policyId: this.fb.nonNullable.control('', Validators.required),
    description: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(4000),
    ]),
    incidentLocation: this.fb.nonNullable.control(''),
    claimedAmount: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
    ]),
    vehiclePlate: this.fb.nonNullable.control(''),
    thirdPartyInvolved: this.fb.nonNullable.control(false),
    propertyAddress: this.fb.nonNullable.control(''),
    damageType: this.fb.nonNullable.control(''),
  });

  readonly openCount = computed(
    () => this.claims().filter((claim) => claim.status !== 'SETTLED' && claim.status !== 'REJECTED').length,
  );
  readonly awaitingInfoCount = computed(
    () => this.claims().filter((claim) => claim.status === 'INFO_REQUESTED').length,
  );
  readonly decidedCount = computed(
    () =>
      this.claims().filter(
        (claim) =>
          claim.status === 'APPROVED' ||
          claim.status === 'REJECTED' ||
          claim.status === 'SETTLED',
      ).length,
  );
  readonly policyOptions = computed(() =>
    this.policies().map((policy) => ({
      label: `${policy.policyNumber} · ${claimTypeLabel(policy.policyType)} · ${policy.marketCode}`,
      value: policy.id,
    })),
  );
  readonly timeline = computed(() =>
    (this.detail()?.timeline ?? [])
      .slice()
      .reverse()
      .map((event) => ({
        ...event,
        icon: eventIcon(event.eventType),
        color: statusColor(event.toStatus ?? event.fromStatus ?? 'SUBMITTED'),
      })),
  );
  readonly detailEntries = computed(() => {
    const details = this.detail()?.incidentDetails ?? {};
    return Object.entries(details).map(([key, value]) => ({
      key: key.replace(/([A-Z])/g, ' $1').replace(/^./, (char) => char.toUpperCase()),
      value: String(value),
    }));
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({ claims: this.api.claims(), policies: this.api.policies() }).subscribe({
      next: ({ claims, policies }) => {
        this.claims.set(claims);
        this.policies.set(policies);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Could not load claims',
          detail: errorMessage(error),
        });
      },
    });
  }

  openSubmit(): void {
    const today = new Date();
    const firstPolicy = this.policies()[0]?.id ?? '';
    this.incidentDate.set(formatDate(new Date(), 'yyyy-MM-dd', 'en-US'));
    this.submitForm.reset({
      policyId: firstPolicy,
      description: '',
      incidentLocation: '',
      claimedAmount: null,
      vehiclePlate: '',
      thirdPartyInvolved: false,
      propertyAddress: '',
      damageType: '',
    });
    this.submitVisible.set(true);
  }

  selectedPolicy(): PolicyView | null {
    const id = this.submitForm.controls.policyId.value;
    return this.policies().find((policy) => policy.id === id) ?? null;
  }

  currencyPrefix(): string {
    return this.selectedPolicy()?.currency ?? '';
  }

  submitClaim(): void {
    const incidentDate = this.incidentDate();
    if (this.submitForm.invalid || !incidentDate) {
      this.submitForm.markAllAsTouched();
      return;
    }
    const policy = this.selectedPolicy();
    if (!policy) {
      return;
    }
    const value = this.submitForm.getRawValue();
    if (value.claimedAmount === null) {
      return;
    }

    const incidentDetails: Record<string, unknown> = {};
    if (policy.policyType === 'MOTOR') {
      if (value.vehiclePlate) {
        incidentDetails['vehiclePlate'] = value.vehiclePlate;
      }
      incidentDetails['thirdPartyInvolved'] = value.thirdPartyInvolved;
    } else {
      if (value.propertyAddress) {
        incidentDetails['propertyAddress'] = value.propertyAddress;
      }
      if (value.damageType) {
        incidentDetails['damageType'] = value.damageType;
      }
    }

    const body: CreateClaimRequest = {
      policyId: policy.id,
      incidentDate,
      description: value.description,
      incidentLocation: value.incidentLocation || undefined,
      claimedAmount: value.claimedAmount,
      incidentDetails,
    };

    this.submitting.set(true);
    this.api.submit(body).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.submitVisible.set(false);
        this.toast.add({
          severity: 'success',
          summary: 'Claim submitted',
          detail: `${created.reference} is now with the claims team.`,
        });
        this.load();
        this.openDetail(created.id);
      },
      error: (error) => {
        this.submitting.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Submission failed',
          detail: errorMessage(error),
        });
      },
    });
  }

  openDetail(id: string): void {
    this.detailVisible.set(true);
    this.detailLoading.set(true);
    this.detail.set(null);
    this.api.claim(id).subscribe({
      next: (claim) => {
        this.detail.set(claim);
        this.detailLoading.set(false);
      },
      error: (error) => {
        this.detailLoading.set(false);
        this.detailVisible.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Could not load claim',
          detail: errorMessage(error),
        });
      },
    });
  }

  refreshDetail(): void {
    const claim = this.detail();
    if (!claim) {
      return;
    }
    this.api.claim(claim.id).subscribe({
      next: (updated) => this.detail.set(updated),
    });
  }

  draftFor(id: string): string {
    return this.drafts()[id] ?? '';
  }

  setDraft(id: string, value: string): void {
    this.drafts.update((drafts) => ({ ...drafts, [id]: value }));
  }

  respond(request: InfoRequestView): void {
    const claim = this.detail();
    const response = this.draftFor(request.id).trim();
    if (!claim || !response) {
      return;
    }
    this.respondingId.set(request.id);
    this.api.respond(claim.id, request.id, response).subscribe({
      next: () => {
        this.respondingId.set(null);
        this.toast.add({
          severity: 'success',
          summary: 'Information sent',
          detail: 'The claims team has been notified.',
        });
        this.drafts.update((drafts) => ({ ...drafts, [request.id]: '' }));
        this.refreshDetail();
        this.load();
      },
      error: (error) => {
        this.respondingId.set(null);
        this.toast.add({
          severity: 'error',
          summary: 'Could not send response',
          detail: errorMessage(error),
        });
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  upload(): void {
    const claim = this.detail();
    const file = this.selectedFile();
    if (!claim || !file) {
      return;
    }
    this.uploading.set(true);
    this.api.upload(claim.id, file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.selectedFile.set(null);
        this.toast.add({
          severity: 'success',
          summary: 'Document uploaded',
          detail: 'The file is attached to your claim.',
        });
        this.refreshDetail();
      },
      error: (error) => {
        this.uploading.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Upload failed',
          detail: errorMessage(error),
        });
      },
    });
  }

  transitionLabel(event: ClaimEventView): string {
    if (event.fromStatus && event.toStatus) {
      return `${statusLabel(event.fromStatus)} → ${statusLabel(event.toStatus)}`;
    }
    return statusLabel(event.toStatus) || event.eventType;
  }

  isMotor(type: ClaimType | null | undefined): boolean {
    return type === 'MOTOR';
  }

  hasMotorDetails(): boolean {
    return this.isMotor(this.selectedPolicy()?.policyType);
  }
}
