#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNAnypay : RCTEventEmitter <RCTBridgeModule>

- (void) subscribeCardReaderCallbacks;

@end
