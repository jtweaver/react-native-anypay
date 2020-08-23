import { NativeModules } from 'react-native';

type AnypayType = {
  multiply(a: number, b: number): Promise<number>;
};

const { Anypay } = NativeModules;

export default Anypay as AnypayType;
