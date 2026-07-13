import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ApiErrorResponse } from '../orders/order.model';

interface LoginFormValue {
  email: FormControl<string>;
  password: FormControl<string>;
}

/**
 * Pantalla de login (FR-024). Envía email/password a
 * `POST /api/v1/auth/login` vía `AuthService.login`, guarda el token
 * recibido y navega a `/orders`. El backend es la autoridad final sobre la
 * validez de las credenciales (401 en caso contrario); este formulario solo
 * valida presencia de campos por UX.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  readonly form = new FormGroup<LoginFormValue>({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly submitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  get canSubmit(): boolean {
    return this.form.valid && !this.submitting();
  }

  submit(): void {
    if (!this.canSubmit) {
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();

    this.authService.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/orders']);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(this.extractErrorMessage(error));
      },
    });
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (error.status === 401) {
      return body?.message ?? 'Email o contraseña incorrectos.';
    }
    return body?.message ?? 'No se pudo iniciar sesión. Intenta de nuevo.';
  }
}
