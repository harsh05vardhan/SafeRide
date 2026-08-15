import { z } from "zod";

/** Mirrors the backend's LoginRequest / RegisterRequest validation (AuthController DTOs) so the client rejects bad input before ever calling the API. */
export const loginSchema = z.object({
  identifier: z.string().min(1, "Enter your email or phone number"),
  password: z.string().min(1, "Enter your password"),
});
export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    name: z.string().min(2, "Name must be at least 2 characters").max(100),
    email: z.string().email("Enter a valid email address"),
    phone: z.string().regex(/^\+?[0-9]{10,15}$/, "Enter a valid 10-15 digit phone number"),
    password: z
      .string()
      .regex(/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/, "At least 8 characters, including a letter and a digit"),
    confirmPassword: z.string(),
    role: z.enum(["CUSTOMER", "DRIVER"]),
    licenseNumber: z.string().optional(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  })
  .refine((data) => data.role !== "DRIVER" || !!data.licenseNumber?.trim(), {
    message: "License number is required for drivers",
    path: ["licenseNumber"],
  });
export type RegisterFormValues = z.infer<typeof registerSchema>;

export const profileSchema = z.object({
  name: z.string().min(2, "Name must be at least 2 characters").max(100),
  phone: z.string().regex(/^\+?[0-9]{10,15}$/, "Enter a valid 10-15 digit phone number"),
});
export type ProfileFormValues = z.infer<typeof profileSchema>;
