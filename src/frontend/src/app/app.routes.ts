import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./orders/order-list.component').then((m) => m.OrderListComponent),
  },
  {
    path: 'orders/new',
    canActivate: [authGuard, roleGuard('DISPATCHER')],
    loadComponent: () =>
      import('./orders/order-create.component').then((m) => m.OrderCreateComponent),
  },
  {
    path: 'orders/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./orders/order-detail.component').then((m) => m.OrderDetailComponent),
  },
  {
    path: 'orders/:id/execution',
    canActivate: [authGuard, roleGuard('TECHNICIAN')],
    loadComponent: () =>
      import('./orders/execution-form.component').then((m) => m.ExecutionFormComponent),
  },
  {
    path: 'orders/:id/review',
    canActivate: [authGuard, roleGuard('SUPERVISOR')],
    loadComponent: () => import('./orders/review.component').then((m) => m.ReviewComponent),
  },
  {
    path: 'orders/:id/reassign',
    canActivate: [authGuard, roleGuard('DISPATCHER')],
    loadComponent: () =>
      import('./orders/reassignment.component').then((m) => m.ReassignmentComponent),
  },
  { path: '', redirectTo: 'orders', pathMatch: 'full' },
];
