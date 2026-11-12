package com.winlator.renderer.effects;

import com.winlator.renderer.material.ScreenMaterial;

/**
 * Optional sharpening pass using AMD FidelityFX Super Resolution 1 "RCAS" (Robust Contrast
 * Adaptive Sharpening). This is only the sharpening half of FSR 1: it runs on the finished
 * frame after it has been scaled to the screen. It does not add frames or reduce GPU load.
 *
 * The shader below is a port of FsrRcasF() from AMD's ffx_fsr1.h (non-denoise path).
 *
 * FidelityFX Super Resolution Sample
 *
 * Copyright (c) 2021 Advanced Micro Devices, Inc. All rights reserved.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files(the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions :
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class SharpenEffect extends Effect {
    @Override
    public ScreenMaterial createMaterial() {
        return new ScreenMaterial() {
            @Override
            protected String getFragmentShader() {
                return String.join("\n",
                    "precision highp float;",
                    "precision highp int;",

                    "uniform sampler2D screenTexture;",
                    "uniform vec2 resolution;",

                    "layout(location = 0) out vec4 outFragColor;",

                    // AMD's FSR_RCAS_LIMIT.
                    "const float RCAS_LIMIT = 0.25 - (1.0 / 16.0);",
                    // exp2(-0.25): AMD's sample default of 0.25 stops of sharpness attenuation.
                    "const float SHARPNESS = 0.840896;",

                    "vec4 load(ivec2 p, ivec2 maxP) {",
                        "return texelFetch(screenTexture, clamp(p, ivec2(0), maxP), 0);",
                    "}",

                    "void main() {",
                        "ivec2 maxP = ivec2(resolution) - 1;",
                        "ivec2 sp = ivec2(gl_FragCoord.xy);",

                        // Minimal 3x3 neighborhood:   b
                        //                           d e f
                        //                             h
                        "vec3 b = load(sp + ivec2( 0, -1), maxP).rgb;",
                        "vec3 d = load(sp + ivec2(-1,  0), maxP).rgb;",
                        "vec4 e4 = load(sp, maxP);",
                        "vec3 e = e4.rgb;",
                        "vec3 f = load(sp + ivec2( 1,  0), maxP).rgb;",
                        "vec3 h = load(sp + ivec2( 0,  1), maxP).rgb;",

                        // Min and max of the ring around the center pixel.
                        "vec3 mn4 = min(min(b, d), min(f, h));",
                        "vec3 mx4 = max(max(b, d), max(f, h));",

                        // Limiters. Same math as AMD's; the tiny epsilons only avoid 0/0 on pure black or white.
                        "vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(0.00001));",
                        "vec3 hitMax = (1.0 - max(mx4, e)) / min(4.0 * mn4 - 4.0, vec3(-0.00001));",
                        "vec3 lobeRGB = max(-hitMin, hitMax);",
                        "float lobe = max(-RCAS_LIMIT, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0)) * SHARPNESS;",

                        // Resolve.
                        "float rcpL = 1.0 / (4.0 * lobe + 1.0);",
                        "outFragColor = vec4((lobe * (b + d + h + f) + e) * rcpL, e4.a);",
                    "}"
                );
            }
        };
    }
}
