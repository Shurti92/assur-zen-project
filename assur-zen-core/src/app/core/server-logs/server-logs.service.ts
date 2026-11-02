import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ServerLogsResponse {
  file: string;
  lineCount: number;
  content: string[];
}

@Injectable({ providedIn: 'root' })
export class ServerLogsService {
  constructor(private readonly http: HttpClient) {}

  fetch(lines: number): Observable<ServerLogsResponse> {
    const params = new HttpParams().set('lines', lines);
    return this.http.get<ServerLogsResponse>('/logs', { params });
  }
}
