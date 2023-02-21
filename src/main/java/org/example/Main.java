package org.example;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.PointerPointer;

import java.util.logging.Logger;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_copy;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    public static void main(String[] args) throws Exception {
        AVOutputFormat ofmt = null;
        AVFormatContext ifmt_ctx = new AVFormatContext(null);
        AVFormatContext ofmt_ctx = new AVFormatContext(null);
        AVPacket pkt = new AVPacket();
        int ret;
        int i;
        int[] stream_mapping;
        int stream_index = 0;
        int stream_mapping_size = 0;

        String in_filename = "C:\\shared\\video-sample.flv";
        String out_filename = "C:\\shared\\converted.mp4";

        AVInputFormat avInputFormat = new AVInputFormat(null);
        AVDictionary avDictionary = new AVDictionary(null);
        if ((ret = avformat_open_input(ifmt_ctx, in_filename, avInputFormat, avDictionary)) < 0) {
            LOGGER.severe(String.format("Could not open input file %s", in_filename));
        }

        av_dict_free(avDictionary);

        // Read packets of a media file to get stream information
        if ((ret = avformat_find_stream_info(ifmt_ctx, (PointerPointer) null)) < 0) {
            throw new Exception(
                    "avformat_find_stream_info() error:\tFailed to retrieve input stream information");
        }

        av_dump_format(ifmt_ctx, 0, in_filename, 0);

        if ((ret = avformat_alloc_output_context2(ofmt_ctx, null, null, out_filename)) < 0) {
            throw new Exception(
                    "avformat_alloc_output_context2() error:\tCould not create output context\n");
        }

        stream_mapping_size = ifmt_ctx.nb_streams();
        stream_mapping = new int[stream_mapping_size];

        ofmt = ofmt_ctx.oformat();

        for (int stream_idx = 0; stream_idx < stream_mapping_size; stream_idx++) {
            AVStream out_stream;
            AVStream in_stream = ifmt_ctx.streams(stream_idx);

            AVCodecParameters in_codedpar = in_stream.codecpar();

            if (in_codedpar.codec_type() != AVMEDIA_TYPE_AUDIO &&
                    in_codedpar.codec_type() != AVMEDIA_TYPE_VIDEO &&
                    in_codedpar.codec_type() != AVMEDIA_TYPE_SUBTITLE) {
                stream_mapping[stream_idx] = -1;
                continue;
            }

            stream_mapping[stream_idx] = stream_index;
            stream_index++;

            out_stream = avformat_new_stream(ofmt_ctx, null);

            ret = avcodec_parameters_copy(out_stream.codecpar(), in_codedpar);
            if (ret < 0) {
                LOGGER.severe("Failed to copy codec parameters");
            }
            out_stream.codecpar().codec_tag(0);
        }

        av_dump_format(ofmt_ctx, 0, out_filename, 1);

        if ((ofmt.flags() & AVFMT_NOFILE) == 0) {
            AVIOContext pb = new AVIOContext(null);
            ret = avio_open(pb, out_filename, AVIO_FLAG_WRITE);
            if (ret < 0) {
                throw new Exception("avio_open() error:\tCould not open output file '%s'" + out_filename);
            }
            ofmt_ctx.pb(pb);
        }

        AVDictionary avOutDict = new AVDictionary(null);
        ret = avformat_write_header(ofmt_ctx, avOutDict);
        if (ret < 0) {
            LOGGER.severe("Error occurred when opening output file");
        }

        for (; ; ) {
            AVStream in_stream, out_stream;
            // Return the next frame of a stream.
            if ((ret = av_read_frame(ifmt_ctx, pkt)) < 0) {
                break;
            }

            in_stream = ifmt_ctx.streams(pkt.stream_index());
            if (pkt.stream_index() >= stream_mapping_size ||
                    stream_mapping[pkt.stream_index()] < 0) {
                av_packet_unref(pkt);
                continue;
            }
            System.out.println("pkt.stream_index(): " + pkt.stream_index());

            System.out.println("stream_mapping[pkt.stream_index()]: " + stream_mapping[pkt.stream_index()]);
            pkt.stream_index(stream_mapping[pkt.stream_index()]);
            out_stream = ofmt_ctx.streams(pkt.stream_index());
            // log_packet

            pkt.pts(av_rescale_q_rnd(pkt.pts(), in_stream.time_base(), out_stream.time_base(),
                    AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
            pkt.dts(av_rescale_q_rnd(pkt.dts(), in_stream.time_base(), out_stream.time_base(),
                    AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
            pkt.duration(av_rescale_q(pkt.duration(), in_stream.time_base(), out_stream.time_base()));
            pkt.pos(-1);

            synchronized (ofmt_ctx) {
                ret = av_interleaved_write_frame(ofmt_ctx, pkt);
                if (ret < 0) {
                    throw new Exception("av_write_frame() error:\tWhile muxing packet\n");
                }
            }

            av_packet_unref(pkt);

        }

        av_write_trailer(ofmt_ctx);

        avformat_close_input(ifmt_ctx);

        if (!ofmt_ctx.isNull() && (ofmt.flags() & AVFMT_NOFILE) == 0) {
            avio_closep(ofmt_ctx.pb());
        }

        avformat_free_context(ofmt_ctx);


    }
}