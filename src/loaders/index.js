import expressLoader from './express';
import mongooseLoader from './mongoose';

export default async ({ expressApp }) => {
  const mongoConnection = await mongooseLoader();
  console.log('MongoDB Initialized');

  await expressLoader({ app: expressApp });
  console.log('Express Initialized');

  await dependencyInjectorLoader(mongoConnection)
  Logger.info('✌️ Dependency Injector loaded');

  // ... more loaders can be here
}
