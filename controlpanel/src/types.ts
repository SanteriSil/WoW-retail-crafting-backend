export type Profession = {
  id: number;
  name: string;
};

export type Item = {
  id: number;
  name: string;
  finishingIngredient?: boolean;
  profession?: Profession | null;
  quality?: number | null;
  iconUrl?: string | null;
  currentPrice?: number | null;
  currentPriceRecordedAt?: string | null;
};

export type AuthResponse = {
  token: string;
  discordUsername: string;
  avatarUrl: string | null;
};

export type AllowedUser = {
  discordId: string;
  discordUsername: string;
  createdAt: string;
};
