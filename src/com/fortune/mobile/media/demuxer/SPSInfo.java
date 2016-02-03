package com.fortune.mobile.media.demuxer;

import com.fortune.mobile.utils.Logger;

/**
 * Created by xjliu on 2016/1/14.
 *
 */

public class SPSInfo {
    public int width;
    public int height;
    public int profile_idc;
    private Logger logger = Logger.getLogger(getClass());
    public SPSInfo(ByteArray sps) {
        sps.position++;
        profile_idc = sps.readUnsignedByte();
        ExpGolomb eg = new ExpGolomb(sps);
        // constraint_set[0-5]_flag, u(1), reserved_zero_2bits u(2), level_idc u(8)
        eg.readBits(16);
        // skip seq_parameter_set_id
        eg.readUE();
        if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122 || profile_idc == 144) {
            int chroma_format_idc  = eg.readUE();
            if (3 == chroma_format_idc) {
                // separate_colour_plane_flag
                eg.readBits(1);
            }
            // bit_depth_luma_minus8
            eg.readUE();
            // bit_depth_chroma_minus8
            eg.readUE();
            // qpprime_y_zero_transform_bypass_flag
            eg.readBits(1);
            // seq_scaling_matrix_present_flag
            boolean seq_scaling_matrix_present_flag  = eg.readBoolean();
            if (seq_scaling_matrix_present_flag) {
                int imax  = (chroma_format_idc != 3) ? 8 : 12;
                logger.debug("data.position="+sps.position+",imax="+imax);
                for (int i = 0; i < imax; i++) {
                    // seq_scaling_list_present_flag[ i ]
                    if (eg.readBoolean()) {
                        if (i < 6) {
                            scaling_list(16, eg);
                        } else {
                            scaling_list(64, eg);
                        }
                    }
                }
                logger.debug("After scaling_list:data.position="+sps.position);
            }
        }else{
            logger.warn("不认识的profile_idc="+profile_idc);
        }
        // log2_max_frame_num_minus4
        eg.readUE();
        int pic_order_cnt_type  = eg.readUE();
        if ( 0 == pic_order_cnt_type ) {
            // log2_max_pic_order_cnt_lsb_minus4
            eg.readUE();
        } else if ( 1 == pic_order_cnt_type ) {
            // delta_pic_order_always_zero_flag
            eg.readBits(1);
            // offset_for_non_ref_pic
            eg.readUE();
            // offset_for_top_to_bottom_field
            eg.readUE();
            int num_ref_frames_in_pic_order_cnt_cycle = eg.readUE();
            for (int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; ++i) {
                // offset_for_ref_frame[ i ]
                eg.readUE();
            }
        }
        // max_num_ref_frames
        eg.readUE();
        // gaps_in_frame_num_value_allowed_flag
        eg.readBits(1);
        int pic_width_in_mbs_minus1 = eg.readUE();
        int pic_height_in_map_units_minus1  = eg.readUE();
        int frame_mbs_only_flag  = eg.readBits(1);
        if (0 == frame_mbs_only_flag) {
            // mb_adaptive_frame_field_flag
            eg.readBits(1);
        }
        // direct_8x8_inference_flag
        eg.readBits(1);
        int frame_cropping_flag  = eg.readBits(1);
        int frame_crop_left_offset=0;
        int frame_crop_right_offset=0;
        int frame_crop_top_offset=0;
        int frame_crop_bottom_offset=0;
        if (frame_cropping_flag!=0) {
            frame_crop_left_offset  = eg.readUE();
            frame_crop_right_offset  = eg.readUE();
            frame_crop_top_offset  = eg.readUE();
            frame_crop_bottom_offset  = eg.readUE();
        }
        width = ((pic_width_in_mbs_minus1 + 1) * 16) - frame_crop_left_offset * 2 - frame_crop_right_offset * 2;
        height = ((2 - frame_mbs_only_flag) * (pic_height_in_map_units_minus1 + 1) * 16) - (frame_crop_top_offset * 2) - (frame_crop_bottom_offset * 2);
    }

    private static void scaling_list(int sizeOfScalingList ,ExpGolomb eg) {
        int lastScale  = 8;
        int nextScale  = 8;
        //int delta_scale;
        for (int j  = 0; j < sizeOfScalingList; j++) {
            if (nextScale != 0) {
                int delta_scale = eg.readSE();
               // System.out.println("delta_scale="+delta_scale+",j="+j);
                nextScale = (lastScale + delta_scale + 256) % 256;
//                nextScale = (lastScale+delta_scale)&0xff;
/*
                if (!i && !next) {// matrix not written, we use the preset one
                    memcpy(factors, jvt_list, size * sizeof(uint8_t));
                    break;
                }
*/

            }
            if(j==0 && nextScale==0){
                break;
            }
            lastScale = (nextScale == 0) ? lastScale : nextScale;
        }
    }

}
