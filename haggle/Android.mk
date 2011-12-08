LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

EXTRA_LIBS_PATH := external

# Do not build for simulator
ifneq ($(TARGET_SIMULATOR),true)

subdirs := $(addprefix $(LOCAL_PATH)/,$(addsuffix /Android.mk, \
                extlibs/libxml2-2.6.31 \
                src/libcpphaggle \
                src/hagglekernel \
                src/libhaggle \
		src/luckyMe \
		src/clitool \
		android \
        ))

include $(subdirs)

endif
