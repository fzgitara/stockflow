import { Component, input } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { ApiError } from '../core/api-error';

/** F6: server errors surfaced clearly, never a blank screen. */
@Component({
  imports: [KeyValuePipe],
  selector: 'app-error-banner',
  template: `
    @if (error(); as e) {
      <div class="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800" role="alert">
        <p class="font-medium">{{ e.message }}</p>
        @if (e.fieldErrors; as fields) {
          <ul class="mt-1 list-inside list-disc">
            @for (msg of fields | keyvalue; track msg.key) {
              <li>{{ msg.key }}: {{ msg.value }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
})
export class ErrorBanner {
  readonly error = input<ApiError | null>(null);
}
