export type RoleName = "CUSTOMER" | "DRIVER" | "ADMIN";

export interface UserSummary {
  id: number;
  name: string;
  email: string;
  phone: string;
  roles: RoleName[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface LoginRequest {
  identifier: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  phone: string;
  password: string;
  role: "CUSTOMER" | "DRIVER";
  licenseNumber?: string;
}

export type UserStatus = "ACTIVE" | "BLOCKED" | "DELETED";

export interface UserProfile {
  id: number;
  name: string;
  email: string;
  phone: string;
  status: UserStatus;
  roles: RoleName[];
  createdAt: string;
}

export interface UpdateProfileRequest {
  name: string;
  phone: string;
}
