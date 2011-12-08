/* Copyright 2008 Uppsala University
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *     
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */ 
#ifndef _LIBHAGGLE_EXPORTS_H
#define _LIBHAGGLE_EXPORTS_H

#ifdef __cplusplus
extern "C" {
#endif

/* Windows compatibility stuff */
#if defined(WIN32) || defined(WINCE)

#ifdef LIBHAGGLE_INTERNAL
#define HAGGLE_API __declspec(dllexport)
#else
#define HAGGLE_API 
#endif
#else
#define HAGGLE_API
#endif

#ifdef __cplusplus
}
#endif

#endif /* _LIBHAGGLE_EXPORTS_H */
