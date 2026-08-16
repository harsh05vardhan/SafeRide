export interface Rating {
  rideId: number;
  score: number;
  comment: string | null;
  createdAt: string;
}

export interface RatingRequest {
  score: number;
  comment?: string;
}
