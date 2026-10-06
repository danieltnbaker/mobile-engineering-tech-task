package com.userhub.di

import com.userhub.data.db.UserDatabase
import com.userhub.data.local.SqlDelightUserLocalDataSource
import com.userhub.data.local.UserLocalDataSource
import com.userhub.data.remote.GoRestApi
import com.userhub.data.remote.createHttpClient
import com.userhub.data.remote.provideApiToken
import com.userhub.data.repository.UserRepository
import com.userhub.data.repository.UserRepositoryImpl
import com.userhub.domain.usecase.AddUserUseCase
import com.userhub.domain.usecase.DeleteUserUseCase
import com.userhub.domain.usecase.GetUsersUseCase
import com.userhub.presentation.AddUserViewModel
import com.userhub.presentation.UserFeedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { createHttpClient(get(), authToken = provideApiToken()) }
    single { GoRestApi(get()) }
    single { UserDatabase(get()) }
    single<UserLocalDataSource> { SqlDelightUserLocalDataSource(get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    factory { GetUsersUseCase(get()) }
    factory { AddUserUseCase(get()) }
    factory { DeleteUserUseCase(get()) }
    viewModel { UserFeedViewModel(get(), get()) }
    viewModel { AddUserViewModel(get()) }
}
