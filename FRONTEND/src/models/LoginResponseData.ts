import type User from "./User";


export default interface LoginResponseData {
  accessToken: string;
  user: User;
  // Refresh token HttpOnly cookie me hota hai — response body me nahi aata.
  // (Backend hamesha null bhejta tha, type galat tha)
  refreshToken: string | null;
  expiresIn: number;
};
