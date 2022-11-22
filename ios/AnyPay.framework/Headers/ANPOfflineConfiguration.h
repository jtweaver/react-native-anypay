//
//  ANPOfflineConfiguration.h
//  AnyPay
//
//  Created by Ankit Gupta on 20/07/22.
//  Copyright © 2022 Dan McCann. All rights reserved.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface ANPOfflineConfiguration : AnyPayModel

@property (nonatomic, strong) NSNumber *maxRetriesAllowed;
@property (nonatomic, strong) NSNumber *retryIntervalInHours;
@property (nonatomic, strong) NSNumber *maxRetriesDayLimit;

@end

NS_ASSUME_NONNULL_END
