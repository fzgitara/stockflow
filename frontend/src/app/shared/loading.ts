import { Component } from '@angular/core';

/** F6: visible loading state for every async screen. */
@Component({
  selector: 'app-loading',
  template: `
    <div class="flex items-center justify-center gap-2 py-10 text-slate-500" role="status">
      <span class="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-slate-600"></span>
      <span class="text-sm">Loading…</span>
    </div>
  `,
})
export class Loading {}
