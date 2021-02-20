import { NativeModules, Image } from 'react-native';

const { TfliteReactNative } = NativeModules;

class Tflite {
  analyze(args, callback_test, callback){
    TfliteReactNative.analyze(args['path'], args['isWater'], callback_test, callback)
  }

  loadModels(){
    TfliteReactNative.loadModels()
  }
}

export default Tflite;
