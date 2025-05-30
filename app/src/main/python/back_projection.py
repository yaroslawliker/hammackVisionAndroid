###
### This module implements the back projection algorithm for image reconstruction.
###

import numpy as np
from scipy.spatial.transform import Rotation

class Camera:
    def __init__(self, fx, fy, skew=0):
        self.fx = fx
        self.fy = fy
        self.skew = skew
        
        self.position = np.zeros(3)
        self.orientation = np.eye(3)
    
    def set_position(self, position):
        self.position = np.array(position)
    def get_position(self):
        return self.position

    def set_orientation(self, orientation):
        self.orientation = np.array(orientation)
    def get_orientation(self):
        return self.orientation

    def get_internal_matrix(self, u, v):
        K = np.array([
            [self.fx, self.skew, u],
            [0, self.fy, v],
            [0, 0, 1]
        ])

        return K    

class Photo:
    def __init__(self, size_pixel_x, size_pixel_y, u = None, v = None):
        self.size_pixel_x = size_pixel_x
        self.size_pixel_y = size_pixel_y

        self.u = size_pixel_x / 2 if u == None else u
        self.v = size_pixel_y / 2 if v == None else v

    def get_u(self):
        return self.u

    def get_v(self):
        return self.v
    
class PhotoPoint:
    def __init__(self, pixel_x, pixel_y, photo_settings: Photo):
        self.photo_settings = photo_settings

        assert pixel_x >= 0 and pixel_x <= photo_settings.size_pixel_x, "pixel_x out of bounds"
        assert pixel_y >= 0 and pixel_y <= photo_settings.size_pixel_y, "pixel_y out of bounds"

        self.point_pixels = np.array([pixel_x, pixel_y])

    def get_point_pixels(self):
        return self.point_pixels
    
    def get_photo_settings(self):
        return self.photo_settings  

class BackProjector:
    def __init__(self, camera: Camera):
        self.camera = camera
    
    def get_direction_to_point_camera(self, point_pixels, u, v):
        # Convert point to homogeneous coordinates
        point_homogeneous = np.array([point_pixels[0], point_pixels[1], 1])
        
        # Get the camera intrinsic matrix
        K = self.camera.get_internal_matrix(u, v)
        
        # Compute the direction vector in camera coordinates
        direction_vector = np.linalg.inv(K) @ point_homogeneous
        
        return direction_vector
    
    def get_direction_to_point_world(self, point_pixels, u, v):
        # Get the direction vector in camera coordinates
        direction_camera = self.get_direction_to_point_camera(point_pixels, u, v)
        
        # Transform the direction vector to world coordinates
        direction_world = self.camera.orientation.T @ direction_camera
        
        return direction_world
    
    def back_project(self, photo_point: PhotoPoint):
        # Get the direction vector in world coordinates
        photo = photo_point.get_photo_settings()
        direction_world = self.get_direction_to_point_world(
                photo_point.get_point_pixels(), 
                photo.get_u(), photo.get_v()
            )
        
        t0 = -self.camera.position[1] / direction_world[1]

        point_3d = self.camera.position + t0 * direction_world
        
        return point_3d
    
if __name__ == "__main__":

    # In centimeters

    # Camera
    pixel_size_cm = 0.0018
    # fx = fy = f / pixel_size_cm = 2.73 / 0.0018 ≈ 1516.67
    f = 27.3 / 0.0018
    cameraY = 5.75

    # Camera orientation
    rotation = Rotation.from_euler('xyz', [-1, 0, 0], degrees=True)
    camera_orientation = rotation.as_matrix()

    galaxy50ACamera = Camera(f, f)

    galaxy50ACamera.set_position([0, cameraY, 0])
    galaxy50ACamera.set_orientation(camera_orientation)

    back_projector = BackProjector(galaxy50ACamera)

    # Photo settings
    size_pixel_x = 1280
    size_pixel_y = 960

    photo = Photo(size_pixel_x, size_pixel_y)

    # Photo (cola1)
    photo_point1 = PhotoPoint(742, 600, photo)

    point_3d = back_projector.back_project(photo_point1)
    print("3D point in world coordinates:", point_3d)

    # Photo (cola1)
    photo_point2 = PhotoPoint(1080, 672, photo)

    point_3d = back_projector.back_project(photo_point2)
    print("3D point in world coordinates:", point_3d)

