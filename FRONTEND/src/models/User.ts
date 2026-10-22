export default interface User {
  id: string;
  name?: string;
  email: string;
  enable: boolean;
  updatedAt?: string;
  createdAt?: string;
  provider: string;

  // NOTE: roles optional hai — backend Set kabhi empty/unmapped bhej sakta hai
  roles?: {
    id: number;
    name: string;
  }[];
}