export type Role = 'CLAIMANT' | 'OFFICER' | 'MANAGER';
export type ClaimType = 'MOTOR' | 'PROPERTY';
export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'INFO_REQUESTED'
  | 'ASSESSED'
  | 'APPROVED'
  | 'REJECTED'
  | 'SETTLED';
export type DecisionOutcome = 'APPROVED' | 'REJECTED';
export type InfoRequestStatus = 'OPEN' | 'ANSWERED';

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  marketCode: string;
  teamId: string | null;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: UserSummary;
}

export interface AuthSession {
  token: string;
  expiresAt: string;
  user: UserSummary;
}

export interface PolicyView {
  id: string;
  policyNumber: string;
  policyType: ClaimType;
  marketCode: string;
  currency: string;
  coverageLimit: number;
  startDate: string;
  endDate: string;
}

export interface ClaimSummary {
  id: string;
  reference: string;
  claimType: ClaimType;
  status: ClaimStatus;
  marketCode: string;
  incidentDate: string;
  currency: string;
  claimedAmount: number;
  submittedAt: string;
  updatedAt: string;
}

export interface ClaimEventView {
  eventType: string;
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus | null;
  note: string | null;
  occurredAt: string;
}

export interface InfoRequestView {
  id: string;
  claimId: string;
  requestedBy: string;
  question: string;
  response: string | null;
  status: InfoRequestStatus;
  requestedAt: string;
  respondedAt: string | null;
}

export interface DocumentView {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface DecisionView {
  outcome: DecisionOutcome;
  reason: string;
  settlementAmount: number | null;
  decidedAt: string;
}

export interface ClaimDetail {
  id: string;
  reference: string;
  claimType: ClaimType;
  status: ClaimStatus;
  marketCode: string;
  incidentDate: string;
  incidentLocation: string | null;
  description: string;
  incidentDetails: Record<string, unknown>;
  currency: string;
  claimedAmount: number;
  submittedAt: string;
  updatedAt: string;
  closedAt: string | null;
  timeline: ClaimEventView[];
  infoRequests: InfoRequestView[];
  documents: DocumentView[];
  decision: DecisionView | null;
}

export interface CreateClaimRequest {
  policyId: string;
  incidentDate: string;
  description: string;
  incidentLocation?: string;
  claimedAmount: number;
  incidentDetails?: Record<string, unknown>;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface QueueItem {
  id: string;
  reference: string;
  claimType: ClaimType;
  status: ClaimStatus;
  marketCode: string;
  incidentDate: string;
  currency: string;
  claimedAmount: number;
  submittedAt: string;
  ageDays: number;
}

export interface WorkloadResponse {
  officerId: string;
  totalOpen: number;
  byStatus: Record<ClaimStatus, number>;
  oldestOpen: QueueItem[];
}

export interface AssessmentView {
  id: string;
  officerId: string;
  notes: string;
  assessedAmount: number;
  createdAt: string;
}

export interface OfficerClaimView {
  id: string;
  reference: string;
  claimType: ClaimType;
  status: ClaimStatus;
  marketCode: string;
  incidentDate: string;
  incidentLocation: string | null;
  description: string;
  incidentDetails: Record<string, unknown>;
  currency: string;
  claimedAmount: number;
  reserveAmount: number | null;
  claimantId: string;
  claimantName: string | null;
  claimantEmail: string | null;
  assignedOfficerId: string | null;
  submittedAt: string;
  updatedAt: string;
  closedAt: string | null;
  timeline: ClaimEventView[];
  infoRequests: InfoRequestView[];
  documents: DocumentView[];
  latestAssessment: AssessmentView | null;
  decision: DecisionView | null;
}

export interface Portfolio {
  openClaims: number;
  unassignedClaims: number;
  awaitingInformation: number;
  approvedNotSettled: number;
  openReserveByCurrency: Record<string, number>;
  openByStatus: Record<ClaimStatus, number>;
  averageOpenAgeDays: number;
  oldestOpenAgeDays: number;
  submittedLast7Days: number;
  decidedLast7Days: number;
  settledLast7Days: number;
  averageCycleTimeDays: number | null;
  approvalRatePct: number | null;
  rejectionRatePct: number | null;
  settledAmountByCurrency: Record<string, number>;
  claimedAmountSettledByCurrency: Record<string, number>;
  settlementRatioPctByCurrency: Record<string, number>;
}

export interface MarketPerformance {
  marketCode: string;
  marketName: string;
  openClaims: number;
  unassignedClaims: number;
  openReserveByCurrency: Record<string, number>;
  agingOver7Days: number;
  submittedLast7Days: number;
  decidedLast7Days: number;
  settledLast7Days: number;
  averageOpenAgeDays: number;
  oldestOpenAgeDays: number;
}

export interface TypeBreakdown {
  claimType: ClaimType;
  openClaims: number;
  openReserveByCurrency: Record<string, number>;
  agingOver7Days: number;
  averageOpenAgeDays: number;
}

export interface StatusBreakdown {
  status: ClaimStatus;
  openClaims: number;
  openReserveByCurrency: Record<string, number>;
  oldestOpenAgeDays: number;
}

export interface OfficerWorkload {
  officerId: string;
  officerName: string;
  marketCode: string;
  openClaims: number;
  byStatus: Record<ClaimStatus, number>;
  agingOver7Days: number;
  oldestOpenAgeDays: number;
  settledLast7Days: number;
  averageCycleTimeDays: number | null;
}

export interface ClaimAttention {
  claimId: string;
  reference: string;
  marketCode: string;
  claimType: ClaimType;
  status: ClaimStatus;
  ageDays: number;
  currency: string;
  claimedAmount: number;
  reserveAmount: number | null;
  assignedOfficerName: string | null;
}

export interface Attention {
  oldestUnassigned: ClaimAttention[];
  awaitingInformation: ClaimAttention[];
  approvedNotSettled: ClaimAttention[];
  topExposure: ClaimAttention[];
}

export interface ManagerDashboard {
  generatedAt: string;
  marketFilter: string | null;
  portfolio: Portfolio;
  byMarket: MarketPerformance[];
  byType: TypeBreakdown[];
  byStatus: StatusBreakdown[];
  officerWorkload: OfficerWorkload[];
  attention: Attention;
}
