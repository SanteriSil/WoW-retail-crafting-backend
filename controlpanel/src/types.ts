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
  multicraftable: boolean;
  multicraftMultiplier: number;
  resourcefulnessFactor: number;
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

// ── Scraper (client-side, F3) ────────────────────────────────────────────────

export type ScrapedRecipe = {
  spellId: number;
  name: string;
  outputItemId: number;
  outputQuantity: number;
  reagents: { itemId: number; quantity: number }[];
  selected: boolean;
  status: "new" | "exists";
};

export type RecipeImportCommand = {
  wowheadSpellId: number;
  recipeName: string;
  outputItemId: number;
  outputQuantity: number;
  professionId: number;
  expansionId: number;
  ingredients: { itemId: number; quantity: number }[];
};

export type ImportResult = {
  added: number;
  updated: number;
  skipped: number;
  errors: string[];
};

// ── Characters (F1) ─────────────────────────────────────────────────────────

export type Character = {
  id: number;
  name: string;
  realm: string;
  iconUrl: string | null;
  professions: CharacterProfessionView[];
  assignedRecipeCount: number;
  createdAt: string;
  updatedAt: string;
};

export type CharacterProfessionView = {
  id: number;
  professionId: number;
  professionName: string;
  multicraftPercent: number;
  resourcefulnessPercent: number;
};

export type CreateCharacterRequest = {
  name: string;
  realm: string;
  professions: {
    professionId: number;
    multicraftPercent: number;
    resourcefulnessPercent: number;
  }[];
};

export type UpdateCharacterRequest = CreateCharacterRequest;

// ── Recipe write payload (U4+U6) ─────────────────────────────────────────────

export type RecipeWritePayload = {
  name: string;
  wowheadSpellId: number | null;
  outputItemId: number;
  outputQuantity: number;
  professionId: number;
  expansionId: number;
  source: string;
  ingredients: { itemId: number; quantity: number }[];
  optionalIngredientGroups: {
    slotIndex: number;
    label: string;
    options: { itemId: number; quantity: number }[];
  }[];
  multicraftable: boolean;
  multicraftMultiplier: number;
  resourcefulnessFactor: number;
};

// ── Dashboard (F2) ──────────────────────────────────────────────────────────

export type DashboardCraft = {
  characterId: number;
  characterName: string;
  characterIconUrl: string | null;
  recipeId: number;
  recipeName: string;
  professionId: number;
  professionName: string;
  outputItemId: number;
  outputItemName: string;
  outputQuantity: number;
  baseProfit: ProfitEstimate;
  adjustedProfit: ProfitEstimate;
  isMulticraftable: boolean;
  multicraftMultiplier: number;
  resourcefulnessFactor: number;
  multicraftPercent: number;
  resourcefulnessPercent: number;
  missingPriceItemIds: number[];
};

export type CraftOverrides = {
  [key: string]: {
    multicraftMultiplier?: number;
    resourcefulnessFactor?: number;
  };
};

export type DashboardResponse = {
  crafts: DashboardCraft[];
  totalBaseProfit: number;
  totalAdjustedProfit: number;
  totalCrafts: number;
};

// ── Calculator (U10) ────────────────────────────────────────────────────────

export type CalculatorEntry = {
  characterId: number;
  recipeId: number;
  quantity: number;
};
