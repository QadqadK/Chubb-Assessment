import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { INSIGHTS_API } from './api-config';
import { ManagerDashboard } from './models';

@Injectable({ providedIn: 'root' })
export class ManagerApi {
  private readonly http = inject(HttpClient);

  dashboard(market?: string | null) {
    let params = new HttpParams();
    if (market) {
      params = params.set('market', market);
    }
    return this.http.get<ManagerDashboard>(`${INSIGHTS_API}/manager/dashboard`, { params });
  }
}
