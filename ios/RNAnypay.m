#import "RNAnypay.h"
#import <React/RCTConvert.h>
@import AnyPay;

@interface RNAnypay ()

@property (nonatomic) AnyPayTerminal *terminal;
@property (nonatomic) ANPProPayEndpoint *endpoint;
@property (nonatomic) AnyPayTransaction *transaction;
@property (nonatomic) bool cardReaderConnected;

@end

@implementation RNAnypay

RCT_EXPORT_MODULE();

- (NSArray<NSString *> *)supportedEvents
{
  return @[@"CardReaderConnected", @"CardReaderError", @"CardReaderDisconnected", @"CardReaderConnectionFailed", @"CardReaderEvent"];
}

RCT_REMAP_METHOD(initialize,
                 config:(NSDictionary *)config
                 initializeWithResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)
{
    [AnyPay initialise];

    ANPProPayEndpoint *endpoint = [ANPProPayEndpoint new];
    endpoint.integrationServerURI = [RCTConvert NSString:config[@"integrationServerUri"]];
    endpoint.gatewayUrl = [RCTConvert NSString:config[@"gatewayUrl"]];

    @try {
        self.terminal = [AnyPayTerminal restoreState];
    } @catch (ANPTerminalNotActivatedException *exception) {
        self.terminal = [AnyPayTerminal initialize:endpoint];
    }

    self.endpoint = (ANPProPayEndpoint *)self.terminal.endpoint;

    [self subscribeCardReaderCallbacks];

    NSLog(@"Whole config --> %@", config);

    self.endpoint.accountNumber = [RCTConvert NSString:config[@"accountNumber"]];
    self.endpoint.x509Certificate = [RCTConvert NSString:config[@"x509Certificate"]];
    self.endpoint.certStr = [RCTConvert NSString:config[@"certStr"]];
    self.endpoint.terminalID = [RCTConvert NSString:config[@"terminalId"]];

    NSLog(@"integrationServerURI --> %@", self.endpoint.integrationServerURI);
    NSLog(@"gatewayUrl --> %@", self.endpoint.gatewayUrl);
    NSLog(@"accountNumber --> %@", self.endpoint.accountNumber);
    NSLog(@"x509Certificate --> %@", self.endpoint.x509Certificate);
    NSLog(@"certStr --> %@", self.endpoint.certStr);
    NSLog(@"terminalID --> %@", self.endpoint.terminalID);

    [self.terminal saveState];

    [AnyPay getSupportKey:@"pergopass" completionHandler:^(NSString *supportKey) {
        NSLog(@"Support Key - %@", supportKey);
    }];

    // Change default logger configuration
    ANPLogger *logger = [ANPLogStream getLogger:@"device"];
    ANPLogConfigurationProperties *logConfig = logger.configuration;
    logConfig.remoteLoggingEnabled = TRUE;
    logConfig.logLevel = @"DEBUG";
    [logger applyConfiguration:logConfig];

    [self.endpoint validateConfiguration:^(BOOL authenticated, ANPMeaningfulError * _Nullable error) {
        if (authenticated) {
            NSLog(@"Terminal Validated");
            resolve(@{@"success": @TRUE});
        }
        else {
            reject(@"FAILED", error.message, nil);
        }
    }];
}

RCT_REMAP_METHOD(connectBluetooth,
                 connectBluetoothWithResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)
{
    [[ANPCardReaderController sharedController] connectBluetoothReader:^(NSArray<ANPBluetoothDevice *> * _Nullable readers) {
        NSLog(@"Found BT devices --> %@", readers);

        resolve(readers.description);
    }];
}

RCT_REMAP_METHOD(isReaderConnected,
                 isReaderConnectedWithResolver:(RCTPromiseResolveBlock)resolve
                 withRejector:(RCTPromiseRejectBlock)reject)
{
    if (self.cardReaderConnected) {
        resolve(@TRUE);
    } else {
        resolve(@FALSE);
    }
}

RCT_EXPORT_METHOD(disconnect)
{
    [self.transaction cancel];

    [[ANPCardReaderController sharedController] disconnectReader];
}

RCT_REMAP_METHOD(emvSale,
                 params:(NSDictionary *)params
                 emvSaleWithResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"EMV Sale");
    AnyPayTransaction *transaction = [[AnyPayTransaction alloc] initWithType:ANPTransactionType_SALE];

    transaction.totalAmount = [ANPAmount amountWithString:[RCTConvert NSString:params[@"amount"]]];
    transaction.currency = @"USD";

    //Optional Settings
//    transaction.invoiceNumber = [RCTConvert NSString:params[@"invoiceNumber"]];
//    [transaction addCustomProperty:@"shouldStoreCardData" value:[RCTConvert NSNumber:params[@"storeCardData"]]];

    //Postal Code is needed when shouldStoreCardData is set to true
//    transaction.postalCode = [RCTConvert NSString:params[@"postalCode"]];

    self.transaction = transaction;

    [transaction execute:^(ANPTransactionStatus status, ANPMeaningfulError * _Nullable error) {
        if (status == ANPTransactionStatus_APPROVED) {
            NSLog(@"Transaction APPROVED");

            AnyPayTransaction *tr = [ANPDatabase getTransactionWithId:transaction.internalID];
            NSLog(@"Fetched Tranasction %@", tr.externalID);
            resolve(@{@"status": @"APPROVED", @"transactionId": tr.externalID, @"responseText": tr.responseText});
        }
        else if (status == ANPTransactionStatus_DECLINED) {
            NSLog(@"Ref Transaction DECLINED");
            reject(@"DECLINED", @"EMV Sale Transaction DECLINED", nil);
        }
        else {
            NSLog(@"Ref Transaction Failed");
            reject(@"FAILED", error.message, nil);
        }
    } cardReaderEvent:^(ANPMeaningfulMessage * _Nullable message) {
        NSLog(@"On Card Reader Event %@", message.message);
        [self sendEventWithName:@"CardReaderEvent" body:@{@"message": message.message}];
    }];
}

RCT_REMAP_METHOD(cancelEMVSale,
                 cancelEMVSaleWithResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"Cancelling EMV Sale");
    [self.transaction cancel];
    self.transaction = nil;
    resolve(@"Cancelled");
}

- (void)subscribeCardReaderCallbacks {
    [[ANPCardReaderController sharedController] subscribeOnCardReaderConnected:^(AnyPayCardReader * _Nullable cardReader) {
        NSLog(@"OnCardReaderConnected --> %@", cardReader.productID);
        self.cardReaderConnected = true;

        [self sendEventWithName:@"CardReaderConnected" body:@{}];
    }];

    [[ANPCardReaderController sharedController] subscribeOnCardReaderError:^(ANPMeaningfulError * _Nullable error) {
        NSLog(@"OnCardReaderError --> %@", error.message);

        [self sendEventWithName:@"CardReaderError" body:@{@"error": error.message}];
    }];

    [[ANPCardReaderController sharedController] subscribeOnCardReaderDisConnected:^{
        NSLog(@"OnCardReaderDisConnected ");
        self.cardReaderConnected = false;

        [self sendEventWithName:@"CardReaderDisconnected" body:@{@"message": @"Card reader disconnected"}];
    }];

    [[ANPCardReaderController sharedController] subscribeOnCardReaderConnectionFailed:^(ANPMeaningfulError * _Nullable error) {
        NSLog(@"OnCardReaderConnectionFailed --> %@", error.message);
        self.cardReaderConnected = false;

        [self sendEventWithName:@"CardReaderConnectionFailed" body:@{@"error": error.message}];
    }];
}

- (void)unsubscribeCardReaderCallbacks {
    [[ANPCardReaderController sharedController] unsubscribeOnCardReaderConnected:^(AnyPayCardReader * _Nullable cardReader) {
        NSLog(@"OnCardReaderConnected - Disconnected");
    }];

    [[ANPCardReaderController sharedController] unsubscribeOnCardReaderError:^(ANPMeaningfulError * _Nullable cardReader) {
        NSLog(@"OnCardReaderError - Disconnected");
    }];

    [[ANPCardReaderController sharedController] unsubscribeOnCardReaderDisConnected:^{
        NSLog(@"OnCardReaderDisConnected - Disconnected");
    }];

    [[ANPCardReaderController sharedController] unsubscribeOnCardReaderConnectionFailed:^(ANPMeaningfulError * _Nullable error) {
        NSLog(@"OnCardReaderConnectionFailed - Disconnected");
    }];
}

@end
