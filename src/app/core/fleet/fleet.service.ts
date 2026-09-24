import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AddVehicleRequest,
  AttachContractRequest,
  FleetContractResponse,
  FleetEcheanceResponse,
  FleetImportErrorPage,
  FleetImportPage,
  FleetImportResponse,
  FleetMovementPage,
  FleetMovementType,
  FleetPage,
  FleetRequest,
  FleetResponse,
  FleetStatus,
  FleetType,
  FleetVehiclePage,
  FleetVehicleResponse,
  FleetVehicleStatus,
  RemoveVehicleRequest,
  ReplaceVehicleRequest,
  TransferVehicleRequest,
  VehiculePage,
  VehiculeRequest,
  VehiculeResponse,
  VehiculeStatut,
  VehiculeType,
} from './models/fleet.model';

export interface FleetListOptions {
  fleetNumber?: string;
  clientId?: number;
  name?: string;
  fleetType?: FleetType;
  status?: FleetStatus;
  startDate?: string;
  endDate?: string;
}

export interface VehiculeListOptions {
  immatriculation?: string;
  proprietaireId?: number;
  type?: VehiculeType;
  statut?: VehiculeStatut;
}

export interface FleetVehicleListOptions {
  status?: FleetVehicleStatus;
  vehiculeType?: VehiculeType;
}

export interface FleetMovementListOptions {
  movementType?: FleetMovementType;
  vehicleId?: number;
  dateFrom?: string;
  dateTo?: string;
  userId?: number;
}

@Injectable({ providedIn: 'root' })
export class FleetService {
  constructor(private readonly http: HttpClient) {}

  // ================= Flottes =================

  listFleets(page: number, size: number, options?: FleetListOptions): Observable<FleetPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.fleetNumber) params = params.set('fleetNumber', options.fleetNumber);
    if (options?.clientId != null) params = params.set('clientId', options.clientId);
    if (options?.name) params = params.set('name', options.name);
    if (options?.fleetType) params = params.set('fleetType', options.fleetType);
    if (options?.status) params = params.set('status', options.status);
    if (options?.startDate) params = params.set('startDate', options.startDate);
    if (options?.endDate) params = params.set('endDate', options.endDate);
    return this.http.get<FleetPage>('/fleets', { params });
  }

  /** Toutes les flottes en une fois — dataset restreint, filtrage/pagination géré côté client. */
  listAllFleets(): Observable<FleetPage> {
    return this.listFleets(0, 500);
  }

  getFleet(id: number): Observable<FleetResponse> {
    return this.http.get<FleetResponse>(`/fleets/${id}`);
  }

  createFleet(payload: FleetRequest): Observable<FleetResponse> {
    return this.http.post<FleetResponse>('/fleets', payload);
  }

  updateFleet(id: number, payload: FleetRequest): Observable<FleetResponse> {
    return this.http.put<FleetResponse>(`/fleets/${id}`, payload);
  }

  activateFleet(id: number): Observable<FleetResponse> {
    return this.http.patch<FleetResponse>(`/fleets/${id}/activate`, {});
  }

  suspendFleet(id: number): Observable<FleetResponse> {
    return this.http.patch<FleetResponse>(`/fleets/${id}/suspend`, {});
  }

  closeFleet(id: number): Observable<FleetResponse> {
    return this.http.patch<FleetResponse>(`/fleets/${id}/close`, {});
  }

  getFleetContracts(fleetId: number): Observable<FleetContractResponse[]> {
    return this.http.get<FleetContractResponse[]>(`/fleets/${fleetId}/contracts`);
  }

  attachFleetContract(fleetId: number, payload: AttachContractRequest): Observable<FleetContractResponse> {
    return this.http.post<FleetContractResponse>(`/fleets/${fleetId}/contracts/attach`, payload);
  }

  getFleetEcheances(fleetId: number): Observable<FleetEcheanceResponse[]> {
    return this.http.get<FleetEcheanceResponse[]>(`/fleets/${fleetId}/echeances`);
  }

  deleteVehicule(id: number): Observable<void> {
    return this.http.delete<void>(`/vehicules/${id}`);
  }

  // ================= Véhicules =================

  listVehicules(page: number, size: number, options?: VehiculeListOptions): Observable<VehiculePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.immatriculation) params = params.set('immatriculation', options.immatriculation);
    if (options?.proprietaireId != null) params = params.set('proprietaireId', options.proprietaireId);
    if (options?.type) params = params.set('type', options.type);
    if (options?.statut) params = params.set('statut', options.statut);
    return this.http.get<VehiculePage>('/vehicules', { params });
  }

  getVehicule(id: number): Observable<VehiculeResponse> {
    return this.http.get<VehiculeResponse>(`/vehicules/${id}`);
  }

  createVehicule(payload: VehiculeRequest): Observable<VehiculeResponse> {
    return this.http.post<VehiculeResponse>('/vehicules', payload);
  }

  updateVehicule(id: number, payload: VehiculeRequest): Observable<VehiculeResponse> {
    return this.http.put<VehiculeResponse>(`/vehicules/${id}`, payload);
  }

  // ================= Mouvements =================

  listFleetVehicles(
    fleetId: number,
    page: number,
    size: number,
    options?: FleetVehicleListOptions,
  ): Observable<FleetVehiclePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.status) params = params.set('status', options.status);
    if (options?.vehiculeType) params = params.set('vehiculeType', options.vehiculeType);
    return this.http.get<FleetVehiclePage>(`/fleets/${fleetId}/vehicles`, { params });
  }

  addFleetVehicle(fleetId: number, payload: AddVehicleRequest): Observable<FleetVehicleResponse> {
    return this.http.post<FleetVehicleResponse>(`/fleets/${fleetId}/vehicles`, payload);
  }

  removeFleetVehicle(fleetId: number, payload: RemoveVehicleRequest): Observable<FleetVehicleResponse> {
    return this.http.post<FleetVehicleResponse>(`/fleets/${fleetId}/vehicles/remove`, payload);
  }

  replaceFleetVehicle(fleetId: number, payload: ReplaceVehicleRequest): Observable<FleetVehicleResponse> {
    return this.http.post<FleetVehicleResponse>(`/fleets/${fleetId}/vehicles/replace`, payload);
  }

  transferFleetVehicle(sourceFleetId: number, vehicleId: number, payload: TransferVehicleRequest): Observable<FleetVehicleResponse> {
    return this.http.post<FleetVehicleResponse>(`/fleets/${sourceFleetId}/vehicles/${vehicleId}/transfer`, payload);
  }

  listFleetMovements(
    fleetId: number,
    page: number,
    size: number,
    options?: FleetMovementListOptions,
  ): Observable<FleetMovementPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.movementType) params = params.set('movementType', options.movementType);
    if (options?.vehicleId != null) params = params.set('vehicleId', options.vehicleId);
    if (options?.dateFrom) params = params.set('dateFrom', options.dateFrom);
    if (options?.dateTo) params = params.set('dateTo', options.dateTo);
    if (options?.userId != null) params = params.set('userId', options.userId);
    return this.http.get<FleetMovementPage>(`/fleets/${fleetId}/movements`, { params });
  }

  // ================= Import CSV/Excel =================

  getImportTemplate(fleetId: number, format: 'csv' | 'xlsx'): Observable<Blob> {
    const params = new HttpParams().set('format', format);
    return this.http.get(`/fleets/${fleetId}/vehicles/import/template`, { params, responseType: 'blob' });
  }

  previewImport(fleetId: number, file: File): Observable<FleetImportResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<FleetImportResponse>(`/fleets/${fleetId}/vehicles/import/preview`, formData);
  }

  confirmImport(fleetId: number, importId: number): Observable<FleetImportResponse> {
    return this.http.post<FleetImportResponse>(`/fleets/${fleetId}/vehicles/import/${importId}/confirm`, {});
  }

  listImports(fleetId: number, page: number, size: number, status?: string): Observable<FleetImportPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<FleetImportPage>(`/fleets/${fleetId}/vehicles/import`, { params });
  }

  getImport(fleetId: number, importId: number): Observable<FleetImportResponse> {
    return this.http.get<FleetImportResponse>(`/fleets/${fleetId}/vehicles/import/${importId}`);
  }

  listImportErrors(fleetId: number, importId: number, page: number, size: number): Observable<FleetImportErrorPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<FleetImportErrorPage>(`/fleets/${fleetId}/vehicles/import/${importId}/errors`, { params });
  }
}
