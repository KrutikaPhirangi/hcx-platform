import { configureStore } from '@reduxjs/toolkit'
import tokenReducer from './reducers/token_reducer';
import participantDetailsReducer from './reducers/participant_details_reducer';
export const store = configureStore({
  reducer: {
    
    tokenReducer:tokenReducer,
    participantDetailsReducer:participantDetailsReducer
  },
})

// Infer the `RootState` and `AppDispatch` types from the store itself
export type RootState = ReturnType<typeof store.getState>
// Inferred type: {posts: PostsState, comments: CommentsState, users: UsersState}
export type AppDispatch = typeof store.dispatch