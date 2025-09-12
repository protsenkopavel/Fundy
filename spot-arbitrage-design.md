# Spot Arbitrage Service Design

## Overview
Implement a service for spot data arbitrage that analyzes prices across multiple exchanges and identifies arbitrage opportunities based on price differences, considering withdrawal and deposit availability.

## Requirements
- Analyze spot prices for coins across all supported exchanges
- Determine lowest price exchange with withdrawal capability
- Determine highest price exchange with deposit capability
- Calculate price spread between buy and sell opportunities
- Return 24h volume data for both exchanges
- Provide real-time withdrawal/deposit status from exchanges

## Architecture

### New Components to Create

#### 1. DTOs
- `SpotArbitrageData` - Response DTO containing:
  - Coin name/symbol
  - Buy exchange and withdrawal availability
  - Sell exchange and deposit availability
  - Price spread (percentage)
  - 24h volume on buy exchange
  - 24h volume on sell exchange

- `WithdrawalDepositStatus` - Status for each exchange/asset pair:
  - Exchange type
  - Asset symbol
  - Can withdraw (boolean)
  - Can deposit (boolean)
  - Last updated timestamp

#### 2. Services
- `SpotArbitrageService` - Main business logic service:
  - Collect spot price data from all exchanges
  - Fetch withdrawal/deposit status for each asset
  - Calculate arbitrage opportunities
  - Filter by minimum spread threshold
  - Sort by spread percentage

- `WithdrawalDepositService` - Handle withdrawal/deposit status:
  - Cache withdrawal/deposit data
  - Fetch from exchanges with rate limiting
  - Provide real-time status updates

#### 3. Controller
- `SpotArbitrageController` - REST API endpoint:
  - `POST /api/market/spot/arbitrage/opportunities`
  - Accept filter parameters (exchanges, minimum spread, etc.)
  - Return list of `SpotArbitrageData`

#### 4. Exchange Client Updates
- Add methods to `SpotExchangeClient` interface:
  - `getWithdrawalStatus(String asset)`
  - `getDepositStatus(String asset)`

- Implement in all exchange client implementations
- Add caching layer for status data

### Data Flow

1. **Request Processing**
   - Receive arbitrage opportunities request
   - Validate parameters
   - Get list of target exchanges

2. **Data Collection**
   - Fetch spot prices from all exchanges using `SpotService.collectSpotPriceData()`
   - Fetch withdrawal/deposit status for each asset/exchange pair
   - Combine price and availability data

3. **Arbitrage Calculation**
   - For each coin with prices on multiple exchanges:
     - Find exchange with lowest price + withdrawal enabled
     - Find exchange with highest price + deposit enabled
     - Calculate spread = (sell_price - buy_price) / buy_price * 100
     - Include volume data from both exchanges

4. **Response Generation**
   - Filter opportunities by minimum spread threshold
   - Sort by spread percentage (descending)
   - Format as `SpotArbitrageData` list

### API Specification

#### Request
```json
{
  "exchanges": ["BYBIT", "KUCOIN", "OKX"],
  "minSpread": 0.5,
  "maxResults": 50
}
```

#### Response
```json
[
  {
    "coin": "BTC",
    "buyExchange": "KUCOIN",
    "withdrawalEnabled": true,
    "sellExchange": "BYBIT",
    "depositEnabled": true,
    "priceSpread": 1.25,
    "buyVolume24h": 1250000.50,
    "sellVolume24h": 980000.75
  }
]
```

### Implementation Plan

1. **Phase 1: Core Infrastructure**
   - Create DTOs (`SpotArbitrageData`, `WithdrawalDepositStatus`)
   - Update `SpotExchangeClient` interface
   - Add basic implementations to exchange clients

2. **Phase 2: Business Logic**
   - Implement `SpotArbitrageService`
   - Add withdrawal/deposit status fetching
   - Implement arbitrage calculation logic

3. **Phase 3: API Layer**
   - Create `SpotArbitrageController`
   - Add request validation
   - Implement response formatting

4. **Phase 4: Caching & Optimization**
   - Add caching for withdrawal/deposit status
   - Implement rate limiting for exchange API calls
   - Add error handling and retries

5. **Phase 5: Testing & Validation**
   - Unit tests for calculation logic
   - Integration tests with mock exchange data
   - Performance testing with multiple exchanges

### Dependencies
- Existing `SpotService` for price data collection
- Exchange client implementations
- Caching infrastructure (Redis/Caffeine)
- HTTP client for API calls to exchanges

### Risk Considerations
- API rate limits on exchanges for withdrawal/deposit status
- Data freshness requirements for real-time arbitrage
- Exchange-specific API differences for status endpoints
- Network latency affecting arbitrage opportunities

### Success Criteria
- Successfully identify arbitrage opportunities across exchanges
- Provide accurate withdrawal/deposit status
- Handle API failures gracefully
- Return results within acceptable time frame (< 5 seconds)
- Support all configured exchanges