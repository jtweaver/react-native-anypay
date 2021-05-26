import {
  // EmitterSubscription,
  NativeEventEmitter,
  NativeModules,
} from 'react-native';
const { RNAnypay } = NativeModules;

type AnypayConfig = {
  integrationServerUri: string;
  gatewayUrl: string;
  accountNumber: string;
  x509Certificate: string;
  certStr: string;
  terminalId: string;
};

// type CallbackPayload = {
//   name: string;
//   error: string;
// };
// type CallbackFunction = (payload: CallbackPayload) => void;

type EMVSale = {
  amount: string;
  invoiceNumber?: string;
  storeCardData?: boolean;
  postalCode?: string;
};

type AnypayType = {
  startup(): null;
  test(test: string, location: string): void;
  initialize(config: AnypayConfig): Promise<string>;
  connectBluetooth(): Promise<string>;
  disconnect(): null;
  emvSale(params: EMVSale): Promise<object>;
  cancelEMVSale(): Promise<string>;
  readyForTransaction(config: AnypayConfig): Promise<string>;
  isReaderConnected(): Promise<string>;
};

export const RNAnypayEventsEmitter = new NativeEventEmitter(
  NativeModules.RNAnypay
);

export default RNAnypay as AnypayType;
