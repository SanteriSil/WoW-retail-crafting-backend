export type Item = {
  id: number;
  name: string;
  finishingIngredient?: boolean;
  iconUrl?: string | null;
  currentPriceRecordedAt?: string | null;
};
