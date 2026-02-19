local addonName, ns = ...

---------------------------------------------------------------------------
-- Configuration
---------------------------------------------------------------------------
local SCAN_DELAY = 0.3          -- seconds between commodity queries
local BROWSE_WAIT = 2.0         -- seconds to wait for browse results to load
local SEARCH_RETRY_DELAY = 2.0  -- seconds before retrying 0-result search
local MAX_RETRIES = 4           -- retries per item
local DEBUG = true              -- set false to silence debug prints

---------------------------------------------------------------------------
-- State
---------------------------------------------------------------------------
local isScanning = false
local scanQueue = {}            -- list of itemIDs to scan
local scanIndex = 0
local currentItemID = nil
local currentItemKeys = {}      -- resolved itemKeys for current itemID (may be >1 for multi-quality)
local currentKeyIndex = 0
local resultData = {}
local outputText = ""
local ahOpen = false
local awaitingResults = false
local retryCount = 0

---------------------------------------------------------------------------
-- Helpers
---------------------------------------------------------------------------
local function Debug(msg)
    if DEBUG then
        print("|cFF88CCFF[CE Debug]|r " .. msg)
    end
end

local function ParseItemIDs(text)
    local ids = {}
    local seen = {}
    for id in text:gmatch("%d+") do
        local num = tonumber(id)
        if num and num > 0 and not seen[num] then
            table.insert(ids, num)
            seen[num] = true
        end
    end
    return ids
end

local function GetItemName(itemID)
    local name = C_Item.GetItemNameByID(itemID)
    return name or tostring(itemID)
end

local function PreCacheItems(itemIDs)
    for _, id in ipairs(itemIDs) do
        C_Item.RequestLoadItemDataByID(id)
    end
end

---------------------------------------------------------------------------
-- UI
---------------------------------------------------------------------------
local frame = CreateFrame("Frame", "CommodityExportFrame", UIParent, "BasicFrameTemplateWithInset")
frame:SetSize(520, 520)
frame:SetPoint("CENTER")
frame:SetMovable(true)
frame:EnableMouse(true)
frame:RegisterForDrag("LeftButton")
frame:SetScript("OnDragStart", frame.StartMoving)
frame:SetScript("OnDragStop", frame.StopMovingOrSizing)
frame:Hide()
frame:SetFrameStrata("HIGH")

frame.TitleBg:SetHeight(30)
frame.title = frame:CreateFontString(nil, "OVERLAY", "GameFontHighlightLarge")
frame.title:SetPoint("TOPLEFT", frame.TitleBg, "TOPLEFT", 5, -3)
frame.title:SetText("Commodity Export")

local inputLabel = frame:CreateFontString(nil, "OVERLAY", "GameFontNormal")
inputLabel:SetPoint("TOPLEFT", frame, "TOPLEFT", 15, -35)
inputLabel:SetText("Item IDs (comma, space, or newline separated):")

local inputScroll = CreateFrame("ScrollFrame", "CEInputScroll", frame, "UIPanelScrollFrameTemplate")
inputScroll:SetSize(455, 60)
inputScroll:SetPoint("TOPLEFT", inputLabel, "BOTTOMLEFT", 0, -5)

local inputBg = inputScroll:CreateTexture(nil, "BACKGROUND")
inputBg:SetAllPoints()
inputBg:SetColorTexture(0, 0, 0, 0.3)

local inputBox = CreateFrame("EditBox", "CEInputBox", inputScroll)
inputBox:SetMultiLine(true)
inputBox:SetFontObject("ChatFontNormal")
inputBox:SetWidth(435)
inputBox:SetHeight(60)
inputBox:SetAutoFocus(false)
inputBox:EnableMouse(true)
inputBox:SetScript("OnEscapePressed", function(self) self:ClearFocus() end)
inputBox:SetScript("OnTextChanged", function(self)
    local _, lineHeight = self:GetFont()
    if not lineHeight then return end
    local numLines = self:GetNumLetters() > 0 and select(2, self:GetText():gsub("\n", "\n")) + 1 or 1
    self:SetHeight(math.max(60, numLines * (lineHeight + 2)))
end)
inputScroll:SetScrollChild(inputBox)
inputScroll:EnableMouse(true)
inputScroll:SetScript("OnMouseDown", function() inputBox:SetFocus() end)

local scanButton = CreateFrame("Button", nil, frame, "UIPanelButtonTemplate")
scanButton:SetSize(100, 25)
scanButton:SetPoint("TOPLEFT", inputScroll, "BOTTOMLEFT", 0, -8)
scanButton:SetText("Scan")

local clearButton = CreateFrame("Button", nil, frame, "UIPanelButtonTemplate")
clearButton:SetSize(80, 25)
clearButton:SetPoint("LEFT", scanButton, "RIGHT", 5, 0)
clearButton:SetText("Clear")
clearButton:SetScript("OnClick", function()
    inputBox:SetText("")
    outputText = ""
    ns.outputBox:SetText("")
    ns.statusText:SetText("")
end)

ns.statusText = frame:CreateFontString(nil, "OVERLAY", "GameFontHighlight")
ns.statusText:SetPoint("LEFT", clearButton, "RIGHT", 10, 0)
ns.statusText:SetPoint("RIGHT", frame, "RIGHT", -15, 0)
ns.statusText:SetJustifyH("LEFT")
ns.statusText:SetText("")

local outputLabel = frame:CreateFontString(nil, "OVERLAY", "GameFontNormal")
outputLabel:SetPoint("TOPLEFT", scanButton, "BOTTOMLEFT", 0, -10)
outputLabel:SetText("Results (click inside, Ctrl+A then Ctrl+C to copy):")

local outputScroll = CreateFrame("ScrollFrame", "CEOutputScroll", frame, "UIPanelScrollFrameTemplate")
outputScroll:SetPoint("TOPLEFT", outputLabel, "BOTTOMLEFT", 0, -5)
outputScroll:SetPoint("BOTTOMRIGHT", frame, "BOTTOMRIGHT", -30, 12)

local outputBg = outputScroll:CreateTexture(nil, "BACKGROUND")
outputBg:SetAllPoints()
outputBg:SetColorTexture(0, 0, 0, 0.3)

ns.outputBox = CreateFrame("EditBox", "CEOutputBox", outputScroll)
ns.outputBox:SetMultiLine(true)
ns.outputBox:SetFontObject("ChatFontNormal")
ns.outputBox:SetWidth(435)
ns.outputBox:SetHeight(280)
ns.outputBox:SetAutoFocus(false)
ns.outputBox:EnableMouse(true)
ns.outputBox:SetScript("OnEscapePressed", function(self) self:ClearFocus() end)
ns.outputBox:SetScript("OnChar", function(self) self:SetText(outputText) end)
ns.outputBox:SetScript("OnTextChanged", function(self)
    local _, lineHeight = self:GetFont()
    if not lineHeight then return end
    local text = self:GetText() or ""
    local numLines = select(2, text:gsub("\n", "\n")) + 1
    self:SetHeight(math.max(280, numLines * (lineHeight + 2)))
end)
outputScroll:SetScrollChild(ns.outputBox)
outputScroll:EnableMouse(true)
outputScroll:SetScript("OnMouseDown", function() ns.outputBox:SetFocus() end)

local function SetStatus(msg)
    ns.statusText:SetText(msg or "")
end

---------------------------------------------------------------------------
-- Result Processing
---------------------------------------------------------------------------
local function CollectCommodityResults(itemID)
    local numResults = C_AuctionHouse.GetNumCommoditySearchResults(itemID)
    if numResults == 0 then return end

    for i = 1, numResults do
        local result = C_AuctionHouse.GetCommoditySearchResultInfo(itemID, i)
        if result and result.unitPrice and result.quantity then
            table.insert(resultData, {
                itemID = itemID,
                unitPrice = result.unitPrice,
                quantity = result.quantity,
            })
        end
    end

    Debug(format("  Collected %d price tiers for ID %d", numResults, itemID))
end

---------------------------------------------------------------------------
-- Forward declarations
---------------------------------------------------------------------------
local AdvanceToNextItem
local AdvanceToNextItemKey
local QueryCurrentItemKey

---------------------------------------------------------------------------
-- Phase 1: SendBrowseQuery to populate AH browse results
-- Phase 2: Read browse results, extract itemKeys that match our itemID
-- Phase 3: SendSearchQuery for each resolved itemKey
---------------------------------------------------------------------------
local function PrimeWithBrowseQuery(itemID, callback)
    local itemName = GetItemName(itemID)
    Debug(format("Phase 1: Browse query for \"%s\" (ID %d)", itemName, itemID))

    local browseQuery = {
        searchString = itemName,
        minLevel = 0,
        maxLevel = 0,
        filters = {},
        itemClassFilters = {},
        sorts = {},
    }

    pcall(C_AuctionHouse.SendBrowseQuery, browseQuery)

    -- Wait for browse results to load, then extract matching itemKeys
    C_Timer.After(BROWSE_WAIT, function()
        if not isScanning then return end

        -- Try to get browse results and extract itemKeys matching our itemID
        local keys = {}
        local ok, browseResults = pcall(C_AuctionHouse.GetBrowseResults)
        if ok and browseResults then
            Debug(format("  Browse returned %d results", #browseResults))
            for _, browseResult in ipairs(browseResults) do
                local key = browseResult.itemKey
                if key and key.itemID == itemID then
                    table.insert(keys, key)
                    Debug(format("  Found matching key: itemID=%d, itemLevel=%d, itemSuffix=%d",
                        key.itemID, key.itemLevel or 0, key.itemSuffix or 0))
                end
            end
        else
            Debug(format("  GetBrowseResults: ok=%s, val=%s", tostring(ok), tostring(browseResults)))
        end

        -- If no keys found from browse, fall back to default key
        if #keys == 0 then
            Debug("  No matching keys from browse — using default itemKey")
            table.insert(keys, {
                itemID = itemID,
                itemLevel = 0,
                itemSuffix = 0,
                battlePetSpeciesID = 0,
            })
        end

        callback(keys)
    end)
end

QueryCurrentItemKey = function()
    if currentKeyIndex > #currentItemKeys then
        -- All quality tiers scanned for this itemID — move to next item
        C_Timer.After(SCAN_DELAY, AdvanceToNextItem)
        return
    end

    local itemKey = currentItemKeys[currentKeyIndex]
    retryCount = 0
    awaitingResults = true

    Debug(format("Phase 3: SendSearchQuery ID=%d, level=%d (quality %d/%d)",
        itemKey.itemID, itemKey.itemLevel or 0, currentKeyIndex, #currentItemKeys))

    pcall(C_AuctionHouse.SendSearchQuery, itemKey, {}, false)
end

AdvanceToNextItemKey = function()
    currentKeyIndex = currentKeyIndex + 1
    QueryCurrentItemKey()
end

---------------------------------------------------------------------------
-- Scan Logic
---------------------------------------------------------------------------
local function FinishScan()
    isScanning = false
    currentItemID = nil
    awaitingResults = false

    local lines = {}
    for _, r in ipairs(resultData) do
        table.insert(lines, format("%d,%d,%d", r.itemID, r.unitPrice, r.quantity))
    end
    outputText = table.concat(lines, "\n")
    ns.outputBox:SetText(outputText)

    SetStatus(format("Done! %d rows.", #resultData))
    Debug(format("Scan complete — %d rows", #resultData))
end

AdvanceToNextItem = function()
    scanIndex = scanIndex + 1
    if scanIndex > #scanQueue then
        FinishScan()
        return
    end

    currentItemID = scanQueue[scanIndex]
    currentItemKeys = {}
    currentKeyIndex = 0
    awaitingResults = false

    local name = GetItemName(currentItemID)
    SetStatus(format("Scanning %d/%d: %s...", scanIndex, #scanQueue, name))

    -- Browse → extract itemKeys → query each
    PrimeWithBrowseQuery(currentItemID, function(keys)
        currentItemKeys = keys
        currentKeyIndex = 0
        AdvanceToNextItemKey()
    end)
end

local function StartScan(itemIDs)
    if isScanning then
        SetStatus("Scan already running...")
        return
    end
    if not ahOpen then
        SetStatus("Open the Auction House first!")
        return
    end
    if #itemIDs == 0 then
        SetStatus("No valid item IDs entered.")
        return
    end

    PreCacheItems(itemIDs)

    scanQueue = itemIDs
    scanIndex = 0
    isScanning = true
    awaitingResults = false
    resultData = {}
    outputText = ""
    ns.outputBox:SetText("")

    Debug(format("Starting scan of %d item IDs", #itemIDs))
    C_Timer.After(0.5, AdvanceToNextItem)
end

scanButton:SetScript("OnClick", function()
    local text = inputBox:GetText()
    local ids = ParseItemIDs(text)
    StartScan(ids)
end)

---------------------------------------------------------------------------
-- AH Toggle Button
---------------------------------------------------------------------------
local ahButton = nil

local function CreateAHButton()
    if ahButton then return end
    local parent = AuctionHouseFrame
    if not parent then return end

    ahButton = CreateFrame("Button", "CommodityExportAHButton", parent, "UIPanelButtonTemplate")
    ahButton:SetSize(140, 22)
    ahButton:SetPoint("TOPRIGHT", parent, "TOPRIGHT", -50, -4)
    ahButton:SetText("Commodity Export")
    ahButton:SetFrameStrata("HIGH")
    ahButton:SetScript("OnClick", function()
        if frame:IsShown() then frame:Hide() else frame:Show() end
    end)
end

---------------------------------------------------------------------------
-- Events
---------------------------------------------------------------------------
local eventFrame = CreateFrame("Frame")
eventFrame:RegisterEvent("AUCTION_HOUSE_SHOW")
eventFrame:RegisterEvent("AUCTION_HOUSE_CLOSED")
eventFrame:RegisterEvent("COMMODITY_SEARCH_RESULTS_UPDATED")

eventFrame:SetScript("OnEvent", function(self, event, ...)
    if event == "AUCTION_HOUSE_SHOW" then
        ahOpen = true
        CreateAHButton()

    elseif event == "AUCTION_HOUSE_CLOSED" then
        ahOpen = false
        if isScanning then
            isScanning = false
            currentItemID = nil
            awaitingResults = false
            SetStatus("Scan cancelled — AH closed.")
        end
        frame:Hide()

    elseif event == "COMMODITY_SEARCH_RESULTS_UPDATED" then
        if not isScanning or not awaitingResults or not currentItemID then return end

        local eventItemID = ...
        if eventItemID and eventItemID ~= currentItemID then return end

        local numResults = C_AuctionHouse.GetNumCommoditySearchResults(currentItemID)
        Debug(format("COMMODITY event ID=%d: numResults=%d (attempt %d)", currentItemID, numResults, retryCount + 1))

        if numResults == 0 then
            retryCount = retryCount + 1
            if retryCount <= MAX_RETRIES then
                Debug(format("  0 results — retry %d/%d", retryCount, MAX_RETRIES))
                awaitingResults = false
                C_Timer.After(SEARCH_RETRY_DELAY, function()
                    if not isScanning or currentItemID ~= (eventItemID or currentItemID) then return end
                    awaitingResults = true
                    local itemKey = currentItemKeys[currentKeyIndex]
                    if itemKey then
                        pcall(C_AuctionHouse.SendSearchQuery, itemKey, {}, false)
                    end
                end)
            else
                Debug(format("  Max retries for ID %d quality %d — skipping",
                    currentItemID, currentKeyIndex))
                awaitingResults = false
                C_Timer.After(SCAN_DELAY, AdvanceToNextItemKey)
            end
            return
        end

        -- Success — collect results and advance to next quality tier or item
        awaitingResults = false
        CollectCommodityResults(currentItemID)
        C_Timer.After(SCAN_DELAY, AdvanceToNextItemKey)
    end
end)

---------------------------------------------------------------------------
-- Slash Commands
---------------------------------------------------------------------------
SLASH_COMMODITYEXPORT1 = "/ce"
SLASH_COMMODITYEXPORT2 = "/commodityexport"
SlashCmdList["COMMODITYEXPORT"] = function()
    if not ahOpen then
        print("|cFFFFFF00[CommodityExport]|r Open the Auction House first, then type /ce")
        return
    end
    if frame:IsShown() then frame:Hide() else frame:Show() end
end

print("|cFF00FF00[CommodityExport]|r Loaded — use |cFFFFFF00/ce|r at the AH.")
