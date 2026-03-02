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
  vendorItem?: boolean | null;
  vendorPrice?: number | null;
  quantity?: number | null;
};

export type AuthResponse = {
  token: string;
  discordUsername: string;
  avatarUrl: string | null;
  /** Role name as returned by the backend: OWNER | ADMIN | ALLOWED_USER */
  role: string;
};

export type AllowedUser = {
  discordId: string;
  discordUsername: string;
  /** Role name: ADMIN | ALLOWED_USER */
  role: string;
  createdAt: string;
};

// ── Expansions ────────────────────────────────────────────────────────────────

export type Expansion = {
  id: number;
  name: string;
  slug: string;
};

// ── Recipe types (matches RecipeDTO / RecipeSummaryDTO on the backend) ────────

export type ProfitEstimate = {
  outputRevenue: number;
  ingredientCost: number;
  profit: number;
  auctionHouseFee: number;
  /** Item IDs whose currentPrice is NULL — these are excluded from the cost sum */
  missingPrices: number[];
  /** False when any required price is missing */
  calculable: boolean;
};

export type RecipeItemView = {
  id: number;
  name: string;
  currentPrice: number | null;
  iconUrl: string | null;
};

export type IngredientView = {
  id: number;
  item: RecipeItemView;
  quantity: number;
};

export type OptionalIngredientOption = {
  id: number;
  item: RecipeItemView;
  quantity: number;
};

export type OptionalIngredientGroup = {
  id: number;
  slotIndex: number;
  label: string | null;
  options: OptionalIngredientOption[];
};

/** Shape of a single row in GET /recipes (Spring Page content) */
export type RecipeSummary = {
  id: number;
  name: string;
  wowheadSpellId: number | null;
  outputItemId: number;
  outputItemName: string;
  outputQuantity: number;
  professionId: number | null;
  professionName: string | null;
  expansionId: number;
  expansionName: string;
  source: string;
  /** Pre-calculated profit in copper; null when not calculable */
  estimatedProfit: number | null;
  profitCalculable: boolean;
  updatedAt: string;
};

/** Full recipe detail from GET /recipes/{id} */
export type RecipeDetail = {
  id: number;
  name: string;
  wowheadSpellId: number | null;
  outputItem: RecipeItemView;
  outputQuantity: number;
  profession: { id: number; name: string } | null;
  expansion: { id: number; name: string; slug: string };
  source: string;
  ingredients: IngredientView[];
  optionalIngredientGroups: OptionalIngredientGroup[];
  profitEstimate: ProfitEstimate | null;
  createdAt: string;
  updatedAt: string;
};

// ── Pagination & filtering ────────────────────────────────────────────────────

/** Spring Data Page wrapper (matches the JSON structure returned by the backend) */
export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

/** Query parameters accepted by GET /recipes and GET /export/recipes/excel */
export type RecipeFilterParams = {
  page?: number;
  size?: number;
  /** e.g. "name,asc" or "estimatedProfit,desc" */
  sort?: string;
  professionId?: number;
  expansionId?: number;
  search?: string;
  outputItemId?: number;
  ingredientItemId?: number;
};
